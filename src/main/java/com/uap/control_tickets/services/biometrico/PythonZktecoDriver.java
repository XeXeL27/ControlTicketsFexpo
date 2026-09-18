package com.uap.control_tickets.services.biometrico;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uap.control_tickets.models.entity.DispositivoBiometrico;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Driver vía scripts Python (pyzk) ejecutados como procesos externos.
 *
 * {@code zk_probar.py} para el diagnóstico y {@code zk_descargar.py} para la
 * descarga masiva (hablan JSON por stdout). pyzk es el intermediario probado
 * contra equipos reales; este driver solo lo invoca y traduce su salida.
 *
 * El ejecutable se configura con {@code app.biometria.python} (default
 * {@code python3}); si no arranca se prueba con el alterno ({@code python}).
 * Requisito del servidor: {@code pip install pyzk}.
 */
@Component
@RequiredArgsConstructor
public class PythonZktecoDriver implements BiometricoDriver {

    private final BiometriaScripts scripts;
    /** Jackson propio: el proyecto no expone ObjectMapper como bean. */
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.biometria.python:python3}")
    private String pythonConfigurado;

    /** Ejecutable que sí arrancó (se cachea tras el primer intento). */
    private volatile String pythonOk;

    @Override
    public Map<String, String> probarConexion(DispositivoBiometrico d) {
        JsonNode r = ejecutar("zk_probar.py", d);
        Map<String, String> info = new LinkedHashMap<>();
        info.put("equipo", etiqueta(d));
        r.fields().forEachRemaining(e -> {
            if (!"estado".equals(e.getKey()) && !"equipo".equals(e.getKey())) {
                info.put(e.getKey(), e.getValue().isNull() ? "?" : e.getValue().asText("?"));
            }
        });
        info.putIfAbsent("estado", "OK");
        return info;
    }

    @Override
    public List<UsuarioBiometrico> leerUsuarios(DispositivoBiometrico d, ProgresoBiometria progreso) {
        JsonNode r = ejecutar("zk_descargar.py", d);
        List<UsuarioBiometrico> usuarios = new ArrayList<>();
        for (JsonNode ju : r.withArray("usuarios")) {
            String pin = ju.path("pin").asText("").trim();
            if (pin.isEmpty()) continue;
            UsuarioBiometrico u = new UsuarioBiometrico(pin);
            u.setUid(ju.path("uid").asInt(-1));
            u.setNombre(ju.path("nombre").asText(""));
            if (!ju.path("version").isNull()) u.setVersionBiometrica(ju.path("version").asText());
            Map<Integer, String> tpls = new LinkedHashMap<>();
            ju.path("templates").fields().forEachRemaining(e -> {
                try {
                    int dedo = Integer.parseInt(e.getKey());
                    String tpl = e.getValue().asText("");
                    if (dedo >= 0 && dedo <= 9 && tpl.length() >= 64) tpls.put(dedo, tpl);
                } catch (NumberFormatException ignored) {
                }
            });
            u.setTemplates(tpls);
            usuarios.add(u);
        }
        String equipo = etiqueta(d);
        progreso.listaDescubierta(equipo, usuarios.size());
        for (int i = 0; i < usuarios.size(); i++) {
            progreso.usuarioListo(equipo, usuarios.get(i), i + 1, usuarios.size());
        }
        return usuarios;
    }

    /**
     * Carga masiva al equipo: escribe el payload en un temporal (por CLI no
     * entrarían miles de templates) y lee las líneas de avance EN VIVO
     * (una por RU) para la barra real. Un vigía mata el proceso si se cuelga.
     */
    @Override
    public void cargarUsuarios(DispositivoBiometrico d, List<UsuarioCarga> usuarios, ProgresoCarga progreso) {
        Path payload;
        try {
            payload = Files.createTempFile("carga-biometrico-", ".json");
            List<Map<String, Object>> lista = new ArrayList<>();
            for (UsuarioCarga u : usuarios) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("pin", u.ru());
                m.put("nombre", u.nombre());
                m.put("templates", u.templates());
                lista.add(m);
            }
            json.writeValue(payload.toFile(), Map.of("usuarios", lista));
        } catch (IOException e) {
            throw new BiometriaException("No se pudo armar la carga: " + e.getMessage(), e);
        }
        String clave = d.getClaveComunicacion() == null ? "" : d.getClaveComunicacion().trim();
        List<String> base = List.of(
                scripts.script("zk_cargar.py").toString(), payload.toString(),
                d.getIp().trim(), String.valueOf(d.getPuerto()),
                String.valueOf(d.getTimeoutMs()), clave);
        // Tope generoso: set_user + templates son varios viajes por RU.
        long esperaMs = 300_000L + (long) usuarios.size() * 20_000L;
        try {
            correrLineas(base, esperaMs, d, progreso);
        } finally {
            try {
                Files.deleteIfExists(payload);
            } catch (IOException ignored) {
            }
        }
    }

    private void correrLineas(List<String> base, long esperaMs, DispositivoBiometrico d,
                              ProgresoCarga progreso) {
        IOException falloArranque = null;
        for (String exe : candidatosPython()) {
            List<String> cmd = new ArrayList<>();
            cmd.add(exe);
            cmd.addAll(base);
            Process proceso;
            try {
                proceso = new ProcessBuilder(cmd).start();
            } catch (IOException e) {
                falloArranque = e; // el exe no existe: probar el siguiente
                continue;
            }
            pythonOk = exe;
            leerLineas(proceso, esperaMs, d, progreso);
            return;
        }
        throw new BiometriaException("No se encontró Python (probé "
                + String.join(", ", candidatosPython()) + "): instalalo y/o fijá app.biometria.python",
                falloArranque);
    }

    private void leerLineas(Process proceso, long esperaMs, DispositivoBiometrico d,
                            ProgresoCarga progreso) {
        ConsumidorSalida err = new ConsumidorSalida(proceso.getErrorStream());
        err.start();
        AtomicBoolean timeout = new AtomicBoolean(false);
        Thread vigia = new Thread(() -> {
            try {
                Thread.sleep(esperaMs);
            } catch (InterruptedException e) {
                return; // terminó a tiempo
            }
            if (proceso.isAlive()) {
                timeout.set(true);
                proceso.destroyForcibly();
            }
        });
        vigia.setDaemon(true);
        vigia.start();
        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(proceso.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank()) continue;
                JsonNode n;
                try {
                    n = json.readTree(linea);
                } catch (Exception e) {
                    continue; // línea que no es JSON: se ignora
                }
                if (n.has("fin")) continue;
                if (n.has("ru")) {
                    progreso.linea(new LineaCarga(
                            n.path("ru").asText(""),
                            n.path("estado").asText("ERROR"),
                            n.path("mensaje").asText("")));
                }
            }
        } catch (IOException e) {
            proceso.destroyForcibly();
            throw new BiometriaException("Se cortó la lectura de la carga: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            proceso.destroyForcibly(); // cancelación u otro: cortar el script
            throw e;
        } finally {
            vigia.interrupt();
        }
        try {
            proceso.waitFor(30, TimeUnit.SECONDS);
            err.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (timeout.get()) {
            throw new BiometriaException("Timeout en la carga a " + etiqueta(d));
        }
        if (proceso.exitValue() != 0) {
            throw new BiometriaException(traducirError("", err.texto(), etiqueta(d)));
        }
    }

    // -------------------------------------------------------------------------

    /** Ejecuta el script y devuelve su JSON (o lanza BiometriaException). */    private JsonNode ejecutar(String script, DispositivoBiometrico d) {
        Path ruta = scripts.script(script);
        String clave = d.getClaveComunicacion() == null ? "" : d.getClaveComunicacion().trim();
        List<String> base = List.of(
                ruta.toString(), d.getIp().trim(),
                String.valueOf(d.getPuerto()), String.valueOf(d.getTimeoutMs()), clave);
        // Margen sobre el timeout del equipo: la descarga masiva tarda minutos.
        long esperaMs = (long) d.getTimeoutMs() + 120_000L;

        List<String> candidatos = candidatosPython();
        IOException falloArranque = null;
        for (String exe : candidatos) {
            List<String> cmd = new ArrayList<>();
            cmd.add(exe);
            cmd.addAll(base);
            try {
                return correr(cmd, esperaMs, script, d);
            } catch (BiometriaException e) {
                throw e; // el script corrió: es respuesta del equipo, no del exe
            } catch (IOException e) {
                falloArranque = e; // el exe no existe: probar el siguiente
            }
        }
        throw new BiometriaException("No se encontró Python (probé " + String.join(", ", candidatos)
                + "): instalalo y/o fijá app.biometria.python", falloArranque);
    }

    private List<String> candidatosPython() {
        List<String> l = new ArrayList<>();
        if (pythonOk != null) l.add(pythonOk);
        l.add(pythonConfigurado);
        l.add(pythonConfigurado.equals("python3") ? "python" : "python3");
        return l.stream().distinct().toList();
    }

    private JsonNode correr(List<String> cmd, long esperaMs, String script, DispositivoBiometrico d)
            throws IOException {
        Process proceso = new ProcessBuilder(cmd).start();
        // Leer las salidas en hilos aparte: si el JSON es grande y nadie lo lee,
        // el proceso se bloquea escribiendo y nunca termina.
        ConsumidorSalida out = new ConsumidorSalida(proceso.getInputStream());
        ConsumidorSalida err = new ConsumidorSalida(proceso.getErrorStream());
        out.start();
        err.start();
        boolean termino;
        try {
            termino = proceso.waitFor(esperaMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BiometriaException("Interrumpido esperando a " + script);
        }
        if (!termino) {
            proceso.destroyForcibly();
            throw new BiometriaException("Timeout ejecutando " + script + " contra " + etiqueta(d));
        }
        try {
            out.join(5000);
            err.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pythonOk = cmd.get(0); // este exe arranca: recordarlo
        String salida = out.texto();
        if (proceso.exitValue() != 0) {
            throw new BiometriaException(traducirError(salida, err.texto(), etiqueta(d)));
        }
        try {
            JsonNode r = json.readTree(salida.isBlank() ? "{}" : salida);
            if ("ERROR".equals(r.path("estado").asText(""))) {
                throw new BiometriaException(traducirError(r.path("mensaje").asText(""), err.texto(), etiqueta(d)));
            }
            return r;
        } catch (BiometriaException e) {
            throw e;
        } catch (Exception e) {
            throw new BiometriaException("El script " + script + " devolvió algo ilegible: "
                    + recorte(salida), e);
        }
    }

    /** Mensajes de pyzk → castellano operativo. */
    private String traducirError(String mensaje, String stderr, String equipo) {
        String m = mensaje == null ? "" : mensaje;
        if (m.contains("Unauthenticated") || m.contains("AUTH")) {
            return "El equipo " + equipo + " rechazó la clave (contraseña incorrecta)";
        }
        if (m.contains("No route") || m.contains("can't reach") || m.contains("Network is unreachable")) {
            return "No hay ruta al equipo " + equipo + " (¿IP mal o fuera de red?)";
        }
        if (m.contains("refused") || m.contains("Connection")) {
            return "El equipo " + equipo + " rechazó la conexión (¿apagado o puerto 4370 cerrado?)";
        }
        if (m.contains("timed out") || m.contains("Timeout") || m.contains("timeout")) {
            return "Timeout con " + equipo + " (¿equipo colgado o red lenta?)";
        }
        if (m.contains("pyzk") && m.contains("pip install")) {
            return "Falta pyzk en el servidor (pip install pyzk)";
        }
        if (!m.isBlank()) return equipo + ": " + m;
        String err = recorte(stderr);
        return err.isBlank() ? "El script falló sin mensaje (" + equipo + ")" : equipo + ": " + err;
    }

    private static String recorte(String s) {
        if (s == null) return "";
        s = s.strip();
        return s.length() <= 500 ? s : "..." + s.substring(s.length() - 500);
    }

    private static String etiqueta(DispositivoBiometrico d) {
        return d.getNombre() + " (" + d.getIp() + ")";
    }

    /** Lee un stream completo en 2º plano. */
    private static class ConsumidorSalida extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        ConsumidorSalida(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                in.transferTo(buf);
            } catch (IOException ignored) {
            }
        }

        String texto() {
            return buf.toString(StandardCharsets.UTF_8);
        }
    }
}
