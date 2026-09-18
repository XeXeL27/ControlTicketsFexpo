package com.uap.control_tickets.services.biometrico;

import com.uap.control_tickets.models.entity.DispositivoBiometrico;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Driver de mentira: devuelve usuarios inventados sin tocar la red.
 *
 * Se usa con {@code app.biometria.simulacion=true} para probar TODO el flujo
 * (barra de progreso, guardado, reporte de duplicados/no encontrados) sin
 * tener un equipo al lado. Los PINs están elegidos para ejercitar cada caso:
 * conviene crear en el sistema estudiantes con RU "1001" y "1002" y dejar
 * "9999" inexistente; "1001" viene además en un segundo equipo → DUPLICADO.
 */
@Component
public class SimulacionBiometricoDriver implements BiometricoDriver {

    @Override
    public Map<String, String> probarConexion(DispositivoBiometrico d) {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("equipo", d.getNombre() + " (" + d.getIp() + ")");
        info.put("plataforma", "SIMULADO");
        info.put("serie", "SIM-0001");
        info.put("nombre", d.getNombre());
        info.put("usuarios", "4");
        info.put("huellas", "3");
        info.put("ssr", "1");
        info.put("versionBiometrica", "10");
        info.put("estado", "OK (simulación, sin red)");
        info.put("latenciaMs", "3");
        return info;
    }

    @Override
    public List<UsuarioBiometrico> leerUsuarios(DispositivoBiometrico d, ProgresoBiometria progreso) {
        List<UsuarioBiometrico> usuarios = new ArrayList<>();

        UsuarioBiometrico a = new UsuarioBiometrico("1001");
        a.setNombre("Simulado Uno");
        a.setUid(1);
        a.setVersionBiometrica("10");
        a.getTemplates().put(0, templateFalso(d, "1001-0"));
        a.getTemplates().put(1, templateFalso(d, "1001-1"));
        usuarios.add(a);

        UsuarioBiometrico b = new UsuarioBiometrico("1002");
        b.setNombre("Simulada Dos");
        b.setUid(2);
        b.setVersionBiometrica("10");
        // Sin templates a propósito → caso SIN_HUELLA.
        usuarios.add(b);

        UsuarioBiometrico c = new UsuarioBiometrico("9999");
        c.setNombre("No Registrado");
        c.setUid(3);
        c.setVersionBiometrica("10");
        c.getTemplates().put(0, templateFalso(d, "9999-0"));
        usuarios.add(c);

        // Mismo RU dos veces en el MISMO equipo → DUPLICADO intra-equipo.
        UsuarioBiometrico dup = new UsuarioBiometrico("1001");
        dup.setNombre("Simulado Uno (repetido)");
        dup.setUid(4);
        dup.setVersionBiometrica("10");
        dup.getTemplates().put(2, templateFalso(d, "1001-2"));
        usuarios.add(dup);

        String equipo = d.getNombre() + " (" + d.getIp() + ")";
        progreso.listaDescubierta(equipo, usuarios.size());
        for (int i = 0; i < usuarios.size(); i++) {
            esperar(150); // para ver la barra subir
            progreso.usuarioListo(equipo, usuarios.get(i), i + 1, usuarios.size());
        }
        return usuarios;
    }

    @Override
    public void cargarUsuarios(DispositivoBiometrico d, List<UsuarioCarga> usuarios, ProgresoCarga progreso) {
        // Finge la subida línea por línea para probar barra + reporte sin equipo.
        for (UsuarioCarga u : usuarios) {
            esperar(150);
            int n = u.templates().size();
            progreso.linea(new LineaCarga(u.ru(), "CARGADO",
                    "creado con " + n + " huella(s)" + (n == 0 ? " (sin huellas en el sistema)" : "")));
        }
    }

    private static String templateFalso(DispositivoBiometrico d, String semilla) {
        byte[] relleno = new byte[512];
        byte[] s = (d.getIp() + "|" + semilla).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = 0; i < relleno.length; i++) relleno[i] = s[i % s.length];
        return Base64.getEncoder().encodeToString(relleno);
    }

    private static void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
