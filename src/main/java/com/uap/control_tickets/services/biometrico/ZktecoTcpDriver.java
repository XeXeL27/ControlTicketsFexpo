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
 * Puerto en Java del protocolo que implementa pyzk (Python) y zk-protocol:
 * cada paquete es cabecera de 8 bytes little-endian
 * {@code [comando:u16, checksum:u16, sesion:u16, reply:u16]} + datos.
 * (Por UDP los mismos paquetes llevan un prefijo mágico "PP\x82\x7d"; por TCP
 * van directos, que es lo que se usa acá.)
 *
 * Secuencia por equipo: CONNECT → DISABLE → leer tabla de usuarios →
 * template por usuario/dedo → ENABLE → EXIT.
 *
 * <p><b>Ojo firmware:</b> ZKTeco no publica el protocolo y cada firmware varía
 * (clásico vs SSR). Los códigos de comando están centralizados abajo como
 * constantes y la lectura de templates prueba SSR primero y clásico después;
 * si un equipo concreto responde distinto, el endpoint
 * {@code POST /api/biometricos/probar-conexion} dice exactamente en qué paso
 * falló, y ahí se ajusta. Ver métodos {@link #pedirTemplateSsr} y
 * {@link #pedirTemplateClasico}.</p>
 */
@Component
public class ZktecoTcpDriver implements BiometricoDriver {

    // --- Comandos (iguales en casi todos los firmwares) -----------------------
    private static final int CMD_CONNECT = 1000;
    private static final int CMD_EXIT = 1001;
    private static final int CMD_ENABLEDEVICE = 1002;
    private static final int CMD_DISABLEDEVICE = 1003;
    private static final int CMD_DB_RRQ = 7;
    private static final int CMD_USERTEMP_RRQ = 9;
    private static final int CMD_OPTIONS_RRQ = 11;
    private static final int CMD_ACK_OK = 2000;
    private static final int CMD_ACK_ERROR = 2001;
    private static final int CMD_ACK_DATA = 2002;
    private static final int CMD_ACK_UNAUTH = 2005;
    private static final int CMD_PREPARE_DATA = 1500;
    private static final int CMD_DATA = 1501;

    private static final int USHRT_MAX = 65535;
    /** Tamaño del registro de usuario clásico/SSR (si el equipo informa otro, se adapta). */
    private static final int REGISTRO_USUARIO = 72;
    /** Cuánto esperar datos "pegados" a una respuesta simple (ms). */
    private static final int VENTANA_DATOS_MS = 400;

    // --------------------------------------------------------------------------

    @Override
    public Map<String, String> probarConexion(DispositivoBiometrico d) {
        long t0 = System.currentTimeMillis();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("equipo", etiqueta(d));
        Sesion s = conectar(d.getIp(), d.getPuerto(), d.getTimeoutMs());
        try {
            info.put("plataforma", pedirOpcion(s, "~Platform"));
            info.put("serie", pedirOpcion(s, "~SerialNumber"));
            info.put("nombre", pedirOpcion(s, "DeviceName"));
            info.put("usuarios", pedirOpcion(s, "UserCount"));
            info.put("huellas", pedirOpcion(s, "FPCount"));
            info.put("ssr", pedirOpcion(s, "~SSR"));
            info.put("versionBiometrica", pedirOpcion(s, "BiometricVersion"));
            info.put("estado", "OK");
            info.put("latenciaMs", String.valueOf(System.currentTimeMillis() - t0));
            return info;
        } finally {
            desconectarSilencioso(s);
        }
    }

    @Override
    public List<UsuarioBiometrico> leerUsuarios(DispositivoBiometrico d, ProgresoBiometria progreso) {
        Sesion s = conectar(d.getIp(), d.getPuerto(), d.getTimeoutMs());
        String equipo = etiqueta(d);
        try {
            comandoSimple(s, CMD_DISABLEDEVICE); // lectura consistente
            byte[] crudo = descargarTablaUsuarios(s);
            List<UsuarioBiometrico> usuarios = parsearUsuarios(crudo, pedirOpcionSilencioso(s, "UserCount"));
            progreso.listaDescubierta(equipo, usuarios.size());
            int i = 0;
            for (UsuarioBiometrico u : usuarios) {
                u.setTemplates(leerTemplates(s, u));
                String version = pedirVersionCacheada(s);
                if (version != null) u.setVersionBiometrica(version);
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
    // Conexión y paquetes
    // -------------------------------------------------------------------------

    /** Sesión TCP con su nº de reply creciente. */
    private static class Sesion {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        int sesionId;
        int replyNo;
        final int timeoutMs;
        String versionCache;

        Sesion(Socket socket, int timeoutMs) throws java.io.IOException {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            this.timeoutMs = timeoutMs;
        }
    }

    private record Respuesta(int codigo, byte[] datos) {
    }

    private Sesion conectar(String ip, int puerto, int timeoutMs) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip, puerto), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            Sesion s = new Sesion(socket, timeoutMs);
            Respuesta r = transaccion(s, CMD_CONNECT, new byte[0]);
            if (r.codigo == CMD_ACK_UNAUTH) {
                cerrar(s);
                throw new BiometriaException("El equipo pide contraseña de comunicación (no soportada): " + ip);
            }
            if (r.codigo != CMD_ACK_OK) {
                cerrar(s);
                throw new BiometriaException("El equipo no aceptó la conexión (código " + r.codigo + "): " + ip);
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

    /** Suma de palabras de 16 bits con complemento (igual que pyzk createChkSum). */
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

    /** Envía un comando y lee su respuesta simple (cabecera + datos pegados). */
    private Respuesta transaccion(Sesion s, int comando, byte[] datos) {
        try {
            ByteBuffer base = ByteBuffer.allocate(8 + datos.length).order(ByteOrder.LITTLE_ENDIAN);
            base.putShort((short) comando).putShort((short) 0)
                    .putShort((short) s.sesionId).putShort((short) s.replyNo)
                    .put(datos);
            byte[] paquete = base.array();
            int chk = checksum(paquete);
            ByteBuffer final_ = ByteBuffer.allocate(paquete.length).order(ByteOrder.LITTLE_ENDIAN);
            final_.putShort((short) comando).putShort((short) chk)
                    .putShort((short) s.sesionId).putShort((short) s.replyNo)
                    .put(datos);
            s.replyNo++;
            s.out.write(final_.array());
            s.out.flush();
            return leerRespuesta(s);
        } catch (SocketTimeoutException e) {
            throw new BiometriaException("El equipo no respondió al comando " + comando + " (timeout)", e);
        } catch (java.io.IOException e) {
            throw new BiometriaException("Error de red con el equipo (comando " + comando + "): " + e.getMessage(), e);
        }
    }

    /** Lee cabecera de 8 bytes + lo que llegue pegado en la ventana corta. */
    private Respuesta leerRespuesta(Sesion s) throws java.io.IOException {
        byte[] cab = new byte[8];
        try {
            s.in.readFully(cab);
        } catch (EOFException e) {
            throw new BiometriaException("El equipo cortó la conexión a mitad de la lectura");
        }
        ByteBuffer bb = ByteBuffer.wrap(cab).order(ByteOrder.LITTLE_ENDIAN);
        int codigo = bb.getShort() & 0xFFFF;
        bb.getShort(); // checksum de la respuesta (no se valida)
        int sesionResp = bb.getShort() & 0xFFFF;
        bb.getShort(); // reply de la respuesta
        if (s.sesionId == 0 && sesionResp != 0) {
            s.sesionId = sesionResp; // el CONNECT informa el id de sesión
        }
        byte[] datos = leerPegado(s);
        return new Respuesta(codigo, datos);
    }

    /** Junta lo que llegue por el socket hasta que se queda callado (ventana corta). */
    private byte[] leerPegado(Sesion s) throws java.io.IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int anterior = s.socket.getSoTimeout();
        s.socket.setSoTimeout(VENTANA_DATOS_MS);
        byte[] tmp = new byte[65536];
        try {
            while (true) {
                int n;
                try {
                    n = s.in.read(tmp);
                } catch (SocketTimeoutException e) {
                    break; // se quedó callado: fin de los datos pegados
                }
                if (n < 0) break;
                buf.write(tmp, 0, n);
                if (n < tmp.length) break;
            }
        } finally {
            s.socket.setSoTimeout(anterior);
        }
        return buf.toByteArray();
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
    // Opciones (diagnóstico: plataforma, serie, conteos…)
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
        Respuesta r = transaccion(s, CMD_OPTIONS_RRQ, opcion.getBytes(StandardCharsets.US_ASCII));
        if (r.codigo != CMD_ACK_OK && r.codigo != CMD_ACK_DATA) {
            return "?";
        }
        String valor = new String(r.datos, StandardCharsets.US_ASCII).trim();
        // La respuesta trae "opcion=valor\0": quedarse con lo de después del '='.
        int eq = valor.indexOf('=');
        if (eq >= 0) valor = valor.substring(eq + 1);
        return valor.replace("\0", "").trim();
    }

    private String pedirVersionCacheada(Sesion s) {
        if (s.versionCache == null) {
            String v = pedirOpcionSilencioso(s, "BiometricVersion");
            s.versionCache = "?".equals(v) ? null : v;
        }
        return s.versionCache;
    }

    // -------------------------------------------------------------------------
    // Tabla de usuarios
    // -------------------------------------------------------------------------

    /**
     * Descarga la tabla completa de usuarios (payload 5 = "dame la tabla").
     * Respuesta grande: viene como PREPARE_DATA + tandas DATA.
     */
    private byte[] descargarTablaUsuarios(Sesion s) {
        byte[] pedido = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(5).array();
        Respuesta r = transaccionConFlujo(s, CMD_USERTEMP_RRQ, pedido, "tabla de usuarios");
        if (r.datos.length < 4) {
            throw new BiometriaException("Tabla de usuarios vacía o ilegible (llegaron " + r.datos.length + " bytes)");
        }
        byte[] datos = r.datos;
        // Los primeros 4 bytes son el tamaño total: si coinciden, saltearlos.
        int declarado = ByteBuffer.wrap(datos, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (declarado == datos.length - 4) {
            byte[] recortado = new byte[datos.length - 4];
            System.arraycopy(datos, 4, recortado, 0, recortado.length);
            return recortado;
        }
        return datos;
    }

    /**
     * Transacción que además junta el flujo PREPARE_DATA + DATA (respuestas
     * grandes). Si la respuesta es simple (ACK_DATA/ACK_OK) la devuelve directo.
     */
    private Respuesta transaccionConFlujo(Sesion s, int comando, byte[] datos, String que) {
        // Enviar a mano (igual que transaccion) porque después cambia la lectura.
        try {
            ByteBuffer base = ByteBuffer.allocate(8 + datos.length).order(ByteOrder.LITTLE_ENDIAN);
            base.putShort((short) comando).putShort((short) 0)
                    .putShort((short) s.sesionId).putShort((short) s.replyNo)
                    .put(datos);
            byte[] paquete = base.array();
            int chk = checksum(paquete);
            ByteBuffer final_ = ByteBuffer.allocate(paquete.length).order(ByteOrder.LITTLE_ENDIAN);
            final_.putShort((short) comando).putShort((short) chk)
                    .putShort((short) s.sesionId).putShort((short) s.replyNo)
                    .put(datos);
            s.replyNo++;
            s.out.write(final_.array());
            s.out.flush();

            byte[] cab = new byte[8];
            s.in.readFully(cab);
            ByteBuffer bb = ByteBuffer.wrap(cab).order(ByteOrder.LITTLE_ENDIAN);
            int codigo = bb.getShort() & 0xFFFF;
            bb.getShort();
            int sesionResp = bb.getShort() & 0xFFFF;
            bb.getShort();
            if (s.sesionId == 0 && sesionResp != 0) s.sesionId = sesionResp;

            if (codigo == CMD_ACK_ERROR) {
                throw new BiometriaException("El equipo rechazó el pedido de " + que);
            }
            if (codigo != CMD_PREPARE_DATA) {
                return new Respuesta(codigo, leerPegado(s));
            }
            // Flujo grande: primer paquete trae el tamaño total (4 bytes LE).
            byte[] inicio = leerPegado(s);
            if (inicio.length < 4) {
                throw new BiometriaException("El equipo anunció datos de " + que + " pero no mandó el tamaño");
            }
            int total = ByteBuffer.wrap(inicio, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (total <= 0 || total > 200_000_000) {
                throw new BiometriaException("Tamaño de " + que + " absurdo (" + total + " bytes)");
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream(total + 4);
            buf.write(inicio, 0, inicio.length);
            int anterior = s.socket.getSoTimeout();
            try {
                while (buf.size() < total + 4) {
                    byte[] cab2 = new byte[8];
                    try {
                        s.in.readFully(cab2);
                    } catch (SocketTimeoutException e) {
                        break; // dejó de mandar: cortar con lo que haya
                    }
                    ByteBuffer bb2 = ByteBuffer.wrap(cab2).order(ByteOrder.LITTLE_ENDIAN);
                    int cod2 = bb2.getShort() & 0xFFFF;
                    bb2.getShort();
                    bb2.getShort();
                    bb2.getShort();
                    if (cod2 != CMD_DATA && cod2 != CMD_ACK_DATA) break;
                    byte[] tanda = leerPegado(s);
                    if (tanda.length == 0) break;
                    buf.write(tanda, 0, tanda.length);
                }
            } finally {
                s.socket.setSoTimeout(anterior);
            }
            return new Respuesta(CMD_ACK_DATA, buf.toByteArray());
        } catch (SocketTimeoutException e) {
            throw new BiometriaException("Timeout descargando " + que, e);
        } catch (BiometriaException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new BiometriaException("Error de red descargando " + que + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parsea los registros de usuario.
     *
     * El PIN (= RU) es un string de dígitos al final del registro (layout
     * clásico y SSR lo traen ahí); el nombre es el string legible más largo.
     * Como el RU siempre es numérico, buscar "el string de dígitos" separa el
     * PIN del nombre en cualquier firmware, sin depender del offset exacto.
     */
    private List<UsuarioBiometrico> parsearUsuarios(byte[] crudo, String conteoInformado) {
        List<UsuarioBiometrico> lista = new ArrayList<>();
        if (crudo.length == 0) return lista;
        int tamRegistro = REGISTRO_USUARIO;
        try {
            int esperados = Integer.parseInt(conteoInformado.trim());
            if (esperados > 0 && crudo.length % esperados == 0
                    && crudo.length / esperados >= 24 && crudo.length / esperados <= 512) {
                tamRegistro = crudo.length / esperados;
            }
        } catch (NumberFormatException ignored) {
        }
        if (crudo.length % tamRegistro != 0 && crudo.length % REGISTRO_USUARIO == 0) {
            tamRegistro = REGISTRO_USUARIO;
        }
        for (int off = 0; off + tamRegistro <= crudo.length; off += tamRegistro) {
            byte[] reg = new byte[tamRegistro];
            System.arraycopy(crudo, off, reg, 0, tamRegistro);
            int uid = ByteBuffer.wrap(reg, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            String pin = mejorPin(reg);
            if (pin == null || pin.isEmpty()) continue; // registro vacío/padding
            UsuarioBiometrico u = new UsuarioBiometrico(pin);
            u.setUid(uid);
            u.setNombre(mejorNombre(reg, pin));
            lista.add(u);
        }
        return lista;
    }

    /** El PIN es el string de dígitos más largo del registro (el RU es numérico). */
    private static String mejorPin(byte[] reg) {
        String mejor = null;
        int i = 0;
        while (i < reg.length) {
            // Saltar bytes que no son dígito.
            while (i < reg.length && !esDigito(reg[i])) i++;
            int ini = i;
            while (i < reg.length && esDigito(reg[i])) i++;
            int fin = i;
            // Termina en NUL o fin de registro: es un string, no parte de un número binario.
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
    // Templates por dedo
    // -------------------------------------------------------------------------

    /** Descarga los 10 dedos; los que el equipo rechaza se toman como "no enrolado". */
    private Map<Integer, String> leerTemplates(Sesion s, UsuarioBiometrico u) {
        Map<Integer, String> templates = new LinkedHashMap<>();
        for (int dedo = 0; dedo <= 9; dedo++) {
            byte[] tpl = pedirTemplateSsr(s, u, dedo);
            if (tpl == null) tpl = pedirTemplateClasico(s, u, dedo);
            if (tpl != null && tpl.length >= 64) {
                templates.put(dedo, Base64.getEncoder().encodeToString(tpl));
            }
        }
        return templates;
    }

    /**
     * Intento SSR (firmwares nuevos): CMD_DB_RRQ con [tipo=2, dedo, uid].
     * Devuelve null si el equipo lo rechaza (dedo no enrolado u otro formato).
     */
    private byte[] pedirTemplateSsr(Sesion s, UsuarioBiometrico u, int dedo) {
        if (u.getUid() < 0) return null;
        try {
            byte[] pedido = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .put((byte) 2).put((byte) 0)
                    .putShort((short) dedo).putShort((short) u.getUid())
                    .array();
            Respuesta r = transaccionConFlujo(s, CMD_DB_RRQ, pedido, "template SSR");
            return normalizarTemplate(r.datos);
        } catch (BiometriaException e) {
            return null;
        }
    }

    /**
     * Intento clásico: CMD_USERTEMP_RRQ con [uid, dedo].
     * Devuelve null si el equipo lo rechaza.
     */
    private byte[] pedirTemplateClasico(Sesion s, UsuarioBiometrico u, int dedo) {
        if (u.getUid() < 0) return null;
        try {
            byte[] pedido = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short) u.getUid()).put((byte) dedo).put((byte) 0)
                    .array();
            Respuesta r = transaccionConFlujo(s, CMD_USERTEMP_RRQ, pedido, "template");
            return normalizarTemplate(r.datos);
        } catch (BiometriaException e) {
            return null;
        }
    }

    /**
     * El equipo a veces antepone el tamaño (2 bytes LE = resto del largo).
     * Si calza, se saltea; si lo que queda es muy chico, no era un template.
     */
    private static byte[] normalizarTemplate(byte[] datos) {
        if (datos == null || datos.length < 64) return null;
        if (datos.length >= 66) {
            int declarado = ByteBuffer.wrap(datos, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            if (declarado == datos.length - 2) {
                byte[] recortado = new byte[datos.length - 2];
                System.arraycopy(datos, 2, recortado, 0, recortado.length);
                return recortado;
            }
        }
        return datos;
    }

    private static String etiqueta(DispositivoBiometrico d) {
        return d.getNombre() + " (" + d.getIp() + ")";
    }
}
