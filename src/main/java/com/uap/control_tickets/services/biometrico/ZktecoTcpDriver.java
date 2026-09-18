package com.uap.control_tickets.services.biometrico;

import com.uap.control_tickets.models.entity.DispositivoBiometrico;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Driver real: habla el protocolo pull de ZKTeco por TCP (puerto 4370).
 *
 * Puerto fiel de <b>pyzk</b> (que a su vez copia el `commpro.c` oficial):
 * cada paquete TCP lleva un encabezado de 8 bytes
 * {@code [0x5050, 0x7D82, largo]} + cabecera de comando de 8 bytes
 * {@code [comando:u16, checksum:u16, sesion:u16, reply:u16]} + datos,
 * todo little-endian.
 *
 * Secuencia por equipo: CONNECT (→ AUTH con {@code make_commkey} si el equipo
 * tiene clave) → DISABLE → tabla de usuarios en bloque → templates en bloque
 * → ENABLE → EXIT.
 *
 * <p>Detalles copiados de pyzk que importan:
 * <ul>
 *   <li>La tabla de usuarios se pide con {@code CMD_USERTEMP_RRQ + int(5)} y
 *       cada registro mide 72 bytes:
 *       {@code uid:u16, priv:u8, pass:8, nombre:24, card:u32, x, grupo:7, x, pin:24}.
 *       El PIN (= RU) son los últimos 24 bytes.</li>
 *   <li>Todos los templates salen en UN bloque con {@code CMD_DB_RRQ + int(2)}:
 *       tamaño total + registros {@code [tam:u16, uid:u16, dedo:s8, valido:s8, template…]}.</li>
 *   <li>Si el bloque de templates no anda, se usa el comando no documentado 88
 *       por usuario/dedo ({@code uid:i16 + dedo:i8}).</li>
 * </ul></p>
 */
@Component
public class ZktecoTcpDriver implements BiometricoDriver {

    // --- Magia del frame TCP --------------------------------------------------
    private static final int TCP_MAGIA_1 = 0x5050;
    private static final int TCP_MAGIA_2 = 0x7D82;

    // --- Comandos --------------------------------------------------------------
    private static final int CMD_CONNECT = 1000;
    private static final int CMD_EXIT = 1001;
    private static final int CMD_ENABLEDEVICE = 1002;
    private static final int CMD_DISABLEDEVICE = 1003;
    private static final int CMD_DB_RRQ = 7;
    private static final int CMD_USERTEMP_RRQ = 9;
    private static final int CMD_OPTIONS_RRQ = 11;
    private static final int CMD_GET_FREE_SIZES = 50;
    private static final int CMD_GET_VERSION = 1100;
    private static final int CMD_AUTH = 1102;
    /** No documentado: un template puntual (uid:i16 + dedo:i8). */
    private static final int CMD_GET_USERTEMP = 88;
    private static final int CMD_ACK_OK = 2000;
    private static final int CMD_ACK_ERROR = 2001;
    private static final int CMD_ACK_DATA = 2002;
    private static final int CMD_ACK_UNAUTH = 2005;
    private static final int CMD_PREPARE_DATA = 1500;
    private static final int CMD_DATA = 1501;

    /** Funciones para los pedidos en bloque (FCT_* de pyzk/const.py). */
    private static final int FCT_FINGERTMP = 2;
    private static final int FCT_USER = 5;

    private static final int USHRT_MAX = 65535;
    private static final int REGISTRO_USUARIO = 72;
    /** Tope de un bloque (7.000 templates de ~1 KB entran sobrados). */
    private static final int BLOQUE_MAXIMO = 256 * 1024 * 1024;

    // --------------------------------------------------------------------------

    @Override
    public Map<String, String> probarConexion(DispositivoBiometrico d) {
        long t0 = System.currentTimeMillis();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("equipo", etiqueta(d));
        Sesion s = conectar(d.getIp(), d.getPuerto(), d.getTimeoutMs(), d.getClaveComunicacion());
        try {
            info.put("plataforma", pedirOpcion(s, "~Platform"));
            info.put("serie", pedirOpcion(s, "~SerialNumber"));
            info.put("nombre", pedirOpcion(s, "~DeviceName"));
            info.put("firmware", pedirFirmware(s));
            int[] conteos = leerConteosSilencioso(s);
            info.put("usuarios", conteos == null ? "?" : String.valueOf(conteos[0]));
            info.put("huellas", conteos == null ? "?" : String.valueOf(conteos[1]));
            info.put("versionHuella", pedirOpcion(s, "~ZKFPVersion"));
            info.put("estado", "OK");
            info.put("latenciaMs", String.valueOf(System.currentTimeMillis() - t0));
            return info;
        } finally {
            desconectarSilencioso(s);
        }
    }

    @Override
    public List<UsuarioBiometrico> leerUsuarios(DispositivoBiometrico d, ProgresoBiometria progreso) {
        Sesion s = conectar(d.getIp(), d.getPuerto(), d.getTimeoutMs(), d.getClaveComunicacion());
        String equipo = etiqueta(d);
        try {
            comandoSimple(s, CMD_DISABLEDEVICE); // lectura consistente
            String version = pedirOpcionSilencioso(s, "~ZKFPVersion");
            List<UsuarioBiometrico> usuarios = descargarUsuarios(s);
            // Templates en bloque (una sola descarga para todo el equipo).
            Map<Integer, Map<Integer, String>> bulk = null;
            try {
                bulk = descargarTemplatesBulk(s);
            } catch (BiometriaException e) {
                bulk = null; // el equipo no soporta el bloque: se pide uno por uno abajo
            }
            progreso.listaDescubierta(equipo, usuarios.size());
            int i = 0;
            for (UsuarioBiometrico u : usuarios) {
                if (version != null) u.setVersionBiometrica(version);
                if (bulk != null) {
                    Map<Integer, String> tpls = bulk.get(u.getUid());
                    if (tpls != null) u.setTemplates(tpls);
                } else {
                    u.setTemplates(leerTemplatesUnoPorUno(s, u));
                }
                i++;
                progreso.usuarioListo(equipo, u, i, usuarios.size());
            }
            return usuarios;
        } finally {
            comandoSilencioso(s, CMD_ENABLEDEVICE);
            desconectarSilencioso(s);
        }
    }

    // -------------------------------------------------------------------------
    // Conexión y frames
    // -------------------------------------------------------------------------

    /** Sesión TCP con su nº de reply creciente. */
    private static class Sesion {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        int sesionId;
        int replyNo;
        final int timeoutMs;

        Sesion(Socket socket, int timeoutMs) throws java.io.IOException {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            this.timeoutMs = timeoutMs;
        }
    }

    private record Respuesta(int codigo, int sesion, byte[] datos) {
    }

    private Sesion conectar(String ip, int puerto, int timeoutMs, String clave) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip, puerto), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            Sesion s = new Sesion(socket, timeoutMs);
            Respuesta r = transaccion(s, CMD_CONNECT, new byte[0]);
            if (r.sesion != 0) s.sesionId = r.sesion; // el CONNECT informa el id de sesión
            if (r.codigo == CMD_ACK_UNAUTH) {
                if (clave == null || clave.isBlank()) {
                    cerrar(s);
                    throw new BiometriaException("El equipo tiene contraseña de comunicación: cargala en la ficha del equipo");
                }
                byte[] commkey;
                try {
                    long n = Long.parseLong(clave.trim());
                    commkey = makeCommkey((int) (n & 0xFFFFFFFFL), s.sesionId);
                } catch (NumberFormatException e) {
                    cerrar(s);
                    throw new BiometriaException("La clave de comunicación debe ser numérica");
                }
                Respuesta ra = transaccion(s, CMD_AUTH, commkey);
                if (ra.codigo != CMD_ACK_OK) {
                    cerrar(s);
                    throw new BiometriaException("Contraseña incorrecta (el equipo la rechazó)");
                }
            } else if (r.codigo != CMD_ACK_OK) {
                cerrar(s);
                throw new BiometriaException("El equipo no aceptó la conexión (código " + r.codigo + ")");
            }
            return s;
        } catch (BiometriaException e) {
            throw e;
        } catch (java.net.SocketTimeoutException e) {
            throw new BiometriaException("Timeout conectando a " + ip + ":" + puerto + " (¿equipo apagado o IP mal?)", e);
        } catch (java.io.IOException e) {
            throw new BiometriaException("No se pudo conectar a " + ip + ":" + puerto + ": " + e.getMessage(), e);
        }
    }

    /**
     * Copia exacta de {@code make_commkey} de pyzk (de {@code commpro.c}):
     * invierte los 32 bits de la clave, suma la sesión, mezcla con "ZKSO",
     * rota las mitades y mezcla con ticks=50.
     */
    static byte[] makeCommkey(int clave, int sesionId) {
        long k = 0;
        for (int i = 0; i < 32; i++) {
            k <<= 1;
            if ((clave & (1 << i)) != 0) k |= 1;
        }
        k += sesionId;
        int k32 = (int) (k & 0xFFFFFFFFL);
        byte[] b = new byte[4];
        b[0] = (byte) (k32 & 0xFF);
        b[1] = (byte) ((k32 >> 8) & 0xFF);
        b[2] = (byte) ((k32 >> 16) & 0xFF);
        b[3] = (byte) ((k32 >> 24) & 0xFF);
        b[0] ^= 'Z';
        b[1] ^= 'K';
        b[2] ^= 'S';
        b[3] ^= 'O';
        int lo = (b[0] & 0xFF) | ((b[1] & 0xFF) << 8);
        int hi = (b[2] & 0xFF) | ((b[3] & 0xFF) << 8);
        b[0] = (byte) (hi & 0xFF);
        b[1] = (byte) ((hi >> 8) & 0xFF);
        b[2] = (byte) (lo & 0xFF);
        b[3] = (byte) ((lo >> 8) & 0xFF);
        int ticks = 50;
        b[0] ^= ticks;
        b[1] ^= ticks;
        b[2] = (byte) ticks;
        b[3] ^= ticks;
        return b;
    }

    private void cerrar(Sesion s) {
        try {
            s.socket.close();
        } catch (java.io.IOException ignored) {
        }
    }

    private void desconectarSilencioso(Sesion s) {
        try {
            comandoSilencioso(s, CMD_EXIT);
        } finally {
            cerrar(s);
        }
    }

    /** Suma de palabras de 16 bits plegada (igual que pyzk). */
    private static int checksum(byte[] paqueteConCero) {
        int suma = 0;
        for (int i = 0; i < paqueteConCero.length; i += 2) {
            int bajo = paqueteConCero[i] & 0xFF;
            int alto = (i + 1 < paqueteConCero.length) ? ((paqueteConCero[i + 1] & 0xFF) << 8) : 0;
            suma += bajo + alto;
            suma %= USHRT_MAX;
        }
        return USHRT_MAX - suma - 1;
    }

    /** Envía un comando con su encabezado TCP. */
    private void enviar(Sesion s, int comando, byte[] datos) throws java.io.IOException {
        ByteBuffer cmd = ByteBuffer.allocate(8 + datos.length).order(ByteOrder.LITTLE_ENDIAN);
        cmd.putShort((short) comando).putShort((short) 0)
                .putShort((short) s.sesionId).putShort((short) s.replyNo)
                .put(datos);
        byte[] paquete = cmd.array();
        int chk = checksum(paquete);
        ByteBuffer final_ = ByteBuffer.allocate(paquete.length).order(ByteOrder.LITTLE_ENDIAN);
        final_.putShort((short) comando).putShort((short) chk)
                .putShort((short) s.sesionId).putShort((short) s.replyNo)
                .put(datos);
        s.replyNo++;
        if (s.replyNo >= USHRT_MAX) s.replyNo -= USHRT_MAX;
        byte[] cuerpo = final_.array();
        ByteBuffer top = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        top.putShort((short) TCP_MAGIA_1).putShort((short) TCP_MAGIA_2).putInt(cuerpo.length);
        s.out.write(top.array());
        s.out.write(cuerpo);
        s.out.flush();
    }

    /** Lee un frame: magia TCP + largo + cabecera de comando + datos. */
    private Respuesta recibir(Sesion s) throws java.io.IOException {
        byte[] top = new byte[8];
        try {
            s.in.readFully(top);
        } catch (EOFException e) {
            throw new BiometriaException("El equipo cortó la conexión a mitad de la lectura");
        }
        ByteBuffer tb = ByteBuffer.wrap(top).order(ByteOrder.LITTLE_ENDIAN);
        int m1 = tb.getShort() & 0xFFFF;
        int m2 = tb.getShort() & 0xFFFF;
        int largo = tb.getInt();
        if (m1 != TCP_MAGIA_1 || m2 != TCP_MAGIA_2) {
            throw new BiometriaException("Respuesta inválida del equipo (sin magia TCP)");
        }
        if (largo < 8 || largo > BLOQUE_MAXIMO) {
            throw new BiometriaException("Tamaño de respuesta absurdo (" + largo + " bytes)");
        }
        byte[] cuerpo = new byte[largo];
        s.in.readFully(cuerpo);
        ByteBuffer bb = ByteBuffer.wrap(cuerpo).order(ByteOrder.LITTLE_ENDIAN);
        int codigo = bb.getShort() & 0xFFFF;
        bb.getShort(); // checksum de la respuesta (no se valida)
        int sesionResp = bb.getShort() & 0xFFFF;
        bb.getShort(); // reply de la respuesta
        byte[] datos = new byte[largo - 8];
        System.arraycopy(cuerpo, 8, datos, 0, datos.length);
        return new Respuesta(codigo, sesionResp, datos);
    }

    /** Envía un comando y lee su frame de respuesta. */
    private Respuesta transaccion(Sesion s, int comando, byte[] datos) {
        try {
            enviar(s, comando, datos);
            return recibir(s);
        } catch (SocketTimeoutException e) {
            throw new BiometriaException("El equipo no respondió al comando " + comando + " (timeout)", e);
        } catch (BiometriaException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new BiometriaException("Error de red con el equipo (comando " + comando + "): " + e.getMessage(), e);
        }
    }

    /**
     * Transacción que además junta el flujo PREPARE_DATA + DATA (respuestas
     * grandes). Si la respuesta es simple la devuelve directo.
     */
    private Respuesta transaccionFlujo(Sesion s, int comando, byte[] datos, String que) {
        try {
            enviar(s, comando, datos);
            Respuesta primera = recibir(s);
            if (primera.codigo == CMD_ACK_ERROR) {
                throw new BiometriaException("El equipo rechazó el pedido de " + que);
            }
            if (primera.codigo != CMD_PREPARE_DATA) {
                return primera;
            }
            if (primera.datos.length < 4) {
                throw new BiometriaException("El equipo anunció " + que + " pero no mandó el tamaño");
            }
            int total = ByteBuffer.wrap(primera.datos, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (total < 0 || total > BLOQUE_MAXIMO) {
                throw new BiometriaException("Tamaño de " + que + " absurdo (" + total + " bytes)");
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream(total + 4);
            buf.write(primera.datos, 0, primera.datos.length);
            while (buf.size() < total + 4) {
                Respuesta tanda;
                try {
                    tanda = recibir(s);
                } catch (SocketTimeoutException e) {
                    break; // dejó de mandar: cortar con lo que haya
                }
                if (tanda.codigo != CMD_DATA && tanda.codigo != CMD_ACK_DATA) break;
                if (tanda.datos.length == 0) break;
                buf.write(tanda.datos, 0, tanda.datos.length);
            }
            return new Respuesta(CMD_ACK_DATA, primera.sesion, buf.toByteArray());
        } catch (SocketTimeoutException e) {
            throw new BiometriaException("Timeout descargando " + que, e);
        } catch (BiometriaException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new BiometriaException("Error de red descargando " + que + ": " + e.getMessage(), e);
        }
    }

    private void comandoSimple(Sesion s, int comando) {
        Respuesta r = transaccion(s, comando, new byte[0]);
        if (r.codigo != CMD_ACK_OK) {
            throw new BiometriaException("Comando " + comando + " rechazado (código " + r.codigo + ")");
        }
    }

    private void comandoSilencioso(Sesion s, int comando) {
        try {
            transaccion(s, comando, new byte[0]);
        } catch (RuntimeException ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // Opciones y conteos (diagnóstico)
    // -------------------------------------------------------------------------

    /** Pide una opción (ej. "~SerialNumber"); si falla devuelve "?". */
    private String pedirOpcionSilencioso(Sesion s, String opcion) {
        try {
            return pedirOpcion(s, opcion);
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private String pedirOpcion(Sesion s, String opcion) {
        byte[] nombre = (opcion + "\0").getBytes(StandardCharsets.US_ASCII);
        Respuesta r = transaccion(s, CMD_OPTIONS_RRQ, nombre);
        if (r.codigo != CMD_ACK_OK && r.codigo != CMD_ACK_DATA) return "?";
        String valor = new String(r.datos, StandardCharsets.US_ASCII);
        int nul = valor.indexOf(0);
        if (nul >= 0) valor = valor.substring(0, nul);
        // La respuesta trae "opcion=valor": quedarse con lo de después del '='.
        int eq = valor.indexOf('=');
        if (eq >= 0) valor = valor.substring(eq + 1);
        return valor.trim();
    }

    private String pedirFirmware(Sesion s) {
        try {
            Respuesta r = transaccion(s, CMD_GET_VERSION, new byte[0]);
            if (r.codigo != CMD_ACK_OK && r.codigo != CMD_ACK_DATA) return "?";
            String v = new String(r.datos, StandardCharsets.US_ASCII);
            int nul = v.indexOf(0);
            return (nul >= 0 ? v.substring(0, nul) : v).trim();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    /** Lee usuarios y huellas con CMD_GET_FREE_SIZES (20 enteros). Null si no responde. */
    private int[] leerConteosSilencioso(Sesion s) {
        try {
            Respuesta r = transaccion(s, CMD_GET_FREE_SIZES, new byte[0]);
            if ((r.codigo != CMD_ACK_OK && r.codigo != CMD_ACK_DATA) || r.datos.length < 80) return null;
            ByteBuffer bb = ByteBuffer.wrap(r.datos).order(ByteOrder.LITTLE_ENDIAN);
            int[] campos = new int[20];
            for (int i = 0; i < 20; i++) campos[i] = bb.getInt();
            return new int[]{ campos[4], campos[6] };
        } catch (RuntimeException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Tabla de usuarios (bloque, 72 bytes por registro)
    // -------------------------------------------------------------------------

    private List<UsuarioBiometrico> descargarUsuarios(Sesion s) {
        byte[] pedido = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(FCT_USER).array();
        Respuesta r = transaccionFlujo(s, CMD_USERTEMP_RRQ, pedido, "tabla de usuarios");
        if (r.datos.length < 4) {
            throw new BiometriaException("Tabla de usuarios vacía o ilegible");
        }
        int declarado = ByteBuffer.wrap(r.datos, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] datos;
        if (declarado >= 0 && declarado == r.datos.length - 4) {
            datos = new byte[r.datos.length - 4];
            System.arraycopy(r.datos, 4, datos, 0, datos.length);
        } else {
            datos = r.datos;
        }
        if (datos.length >= REGISTRO_USUARIO && datos.length % REGISTRO_USUARIO == 0) {
            return parseoExacto(datos);
        }
        return parseoHeuristico(datos); // firmware con otro tamaño: mejor esfuerzo
    }

    /**
     * Layout real pyzk {@code <HB8s24sIx7sx24s}:
     * uid[0:2], nombre[11:35], PIN[48:72].
     */
    private List<UsuarioBiometrico> parseoExacto(byte[] datos) {
        List<UsuarioBiometrico> lista = new ArrayList<>();
        for (int off = 0; off + REGISTRO_USUARIO <= datos.length; off += REGISTRO_USUARIO) {
            int uid = ByteBuffer.wrap(datos, off, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            String pin = cadena(datos, off + 48, 24);
            if (pin.isEmpty()) continue; // registro vacío/padding
            UsuarioBiometrico u = new UsuarioBiometrico(pin);
            u.setUid(uid);
            String nombre = cadena(datos, off + 11, 24);
            u.setNombre(nombre.isEmpty() ? "NN-" + pin : nombre);
            lista.add(u);
        }
        return lista;
    }

    private static String cadena(byte[] datos, int off, int len) {
        int fin = off;
        while (fin < off + len && datos[fin] != 0) fin++;
        String s = new String(datos, off, fin - off, StandardCharsets.UTF_8);
        if (s.contains("�")) {
            s = new String(datos, off, fin - off, Charset.forName("windows-1252"));
        }
        return s.trim();
    }

    /**
     * Respaldo para firmwares con otro tamaño de registro: el PIN (= RU) es el
     * string de dígitos más largo del registro (el RU siempre es numérico).
     */
    private List<UsuarioBiometrico> parseoHeuristico(byte[] crudo) {
        List<UsuarioBiometrico> lista = new ArrayList<>();
        if (crudo.length == 0) return lista;
        int tam = REGISTRO_USUARIO;
        // Si el largo total sugiere otro tamaño regular, adaptarse.
        for (int cand : new int[]{ 72, 28, 76, 80 }) {
            if (crudo.length % cand == 0) {
                tam = cand;
                break;
            }
        }
        for (int off = 0; off + tam <= crudo.length; off += tam) {
            byte[] reg = new byte[tam];
            System.arraycopy(crudo, off, reg, 0, tam);
            int uid = ByteBuffer.wrap(reg, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            String pin = mejorPin(reg);
            if (pin == null || pin.isEmpty()) continue;
            UsuarioBiometrico u = new UsuarioBiometrico(pin);
            u.setUid(uid);
            u.setNombre(mejorNombre(reg, pin));
            lista.add(u);
        }
        return lista;
    }

    /** El PIN es el string de dígitos más largo del registro. */
    private static String mejorPin(byte[] reg) {
        String mejor = null;
        int i = 0;
        while (i < reg.length) {
            while (i < reg.length && !esDigito(reg[i])) i++;
            int ini = i;
            while (i < reg.length && esDigito(reg[i])) i++;
            int fin = i;
            boolean cerrado = fin >= reg.length || reg[fin] == 0;
            if (cerrado && fin - ini >= 1 && (mejor == null || fin - ini > mejor.length())) {
                mejor = new String(reg, ini, fin - ini, StandardCharsets.US_ASCII);
            }
            if (i < reg.length && reg[i] == 0) i++;
            else if (fin == ini) i++;
        }
        return mejor;
    }

    private static boolean esDigito(byte b) {
        return b >= '0' && b <= '9';
    }

    /** El nombre es el string legible más largo que no sea el PIN. */
    private static String mejorNombre(byte[] reg, String pin) {
        String mejor = "";
        int i = 0;
        while (i < reg.length) {
            while (i < reg.length && !esImprimible(reg[i])) i++;
            int ini = i;
            while (i < reg.length && esImprimible(reg[i])) i++;
            if (i - ini >= 2) {
                String cand = decodificar(reg, ini, i - ini).trim();
                if (!cand.equals(pin) && cand.length() > mejor.length()
                        && !(cand.chars().allMatch(Character::isDigit))) {
                    mejor = cand;
                }
            }
            if (i < reg.length) i++;
        }
        return mejor;
    }

    private static boolean esImprimible(byte b) {
        int v = b & 0xFF;
        return (v >= 32 && v < 127) || v >= 160;
    }

    private static String decodificar(byte[] reg, int off, int len) {
        Charset[] intentos = { StandardCharsets.UTF_8, Charset.forName("windows-1252") };
        for (Charset cs : intentos) {
            try {
                return new String(reg, off, len, cs);
            } catch (RuntimeException ignored) {
            }
        }
        return "";
    }

    // -------------------------------------------------------------------------
    // Templates: en bloque, con respaldo uno por uno (comando 88)
    // -------------------------------------------------------------------------

    /**
     * Todos los templates en UNA descarga ({@code CMD_DB_RRQ + int(2)}):
     * tamaño total + registros {@code [tam:u16, uid:u16, dedo:s8, valido:s8, template…]}.
     * Devuelve uid → (dedo → Base64).
     */
    private Map<Integer, Map<Integer, String>> descargarTemplatesBulk(Sesion s) {
        byte[] pedido = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(FCT_FINGERTMP).array();
        Respuesta r = transaccionFlujo(s, CMD_DB_RRQ, pedido, "templates");
        if (r.datos.length < 4) {
            throw new BiometriaException("El equipo no devolvió templates en bloque");
        }
        int total = ByteBuffer.wrap(r.datos, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (total < 0 || total > BLOQUE_MAXIMO) {
            throw new BiometriaException("Tamaño de templates absurdo (" + total + " bytes)");
        }
        Map<Integer, Map<Integer, String>> porUsuario = new LinkedHashMap<>();
        int off = 4;
        int consumidos = 0;
        while (off + 6 <= r.datos.length && consumidos < total) {
            ByteBuffer bb = ByteBuffer.wrap(r.datos, off, 6).order(ByteOrder.LITTLE_ENDIAN);
            int tam = bb.getShort() & 0xFFFF;
            int uid = bb.getShort() & 0xFFFF;
            int dedo = bb.get();
            bb.get(); // válido (se guarda igual)
            if (tam < 6 || off + tam > r.datos.length) break;
            byte[] tpl = new byte[tam - 6];
            System.arraycopy(r.datos, off + 6, tpl, 0, tpl.length);
            if (tpl.length >= 64 && dedo >= 0 && dedo <= 9) {
                porUsuario.computeIfAbsent(uid, k -> new LinkedHashMap<>())
                        .putIfAbsent(dedo, Base64.getEncoder().encodeToString(tpl));
            }
            off += tam;
            consumidos += tam;
        }
        return porUsuario;
    }

    /** Respaldo: pide cada dedo con el comando 88 (uid:i16 + dedo:i8). */
    private Map<Integer, String> leerTemplatesUnoPorUno(Sesion s, UsuarioBiometrico u) {
        Map<Integer, String> templates = new LinkedHashMap<>();
        if (u.getUid() < 0) return templates;
        for (int dedo = 0; dedo <= 9; dedo++) {
            byte[] tpl = pedirTemplate88(s, u.getUid(), dedo);
            if (tpl != null) templates.put(dedo, Base64.getEncoder().encodeToString(tpl));
        }
        return templates;
    }

    private byte[] pedirTemplate88(Sesion s, int uid, int dedo) {
        try {
            byte[] pedido = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short) uid).put((byte) dedo).array();
            Respuesta r = transaccionFlujo(s, CMD_GET_USERTEMP, pedido, "template");
            return normalizar88(r.datos);
        } catch (BiometriaException e) {
            return null; // dedo no enrolado o equipo que no responde: se sigue
        }
    }

    /**
     * La respuesta del comando 88 trae relleno de ceros al final (pyzk recorta
     * el último byte y, si los 6 anteriores son cero, esos también).
     */
    private static byte[] normalizar88(byte[] datos) {
        if (datos == null || datos.length < 70) return null;
        int fin = datos.length - 1;
        boolean seisCeros = true;
        for (int i = 0; i < 6; i++) {
            if (datos[fin - 1 - i] != 0) {
                seisCeros = false;
                break;
            }
        }
        if (seisCeros) fin -= 6;
        int tam = fin;
        if (tam < 64) return null;
        byte[] tpl = new byte[tam];
        System.arraycopy(datos, 0, tpl, 0, tam);
        return tpl;
    }

    private static String etiqueta(DispositivoBiometrico d) {
        return d.getNombre() + " (" + d.getIp() + ")";
    }
}
