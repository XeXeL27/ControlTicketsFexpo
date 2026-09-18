package com.uap.control_tickets.services.biometrico;

import com.uap.control_tickets.models.entity.DispositivoBiometrico;

import java.util.List;
import java.util.Map;

/**
 * Habla con UN biométrico ZKTeco (protocolo pull por TCP 4370).
 *
 * Implementaciones: {@link PythonZktecoDriver} (scripts pyzk, default),
 * {@link ZktecoTcpDriver} (TCP propio, respaldo) y
 * {@link SimulacionBiometricoDriver} (datos inventados para probar el flujo
 * sin equipo, con {@code app.biometria.simulacion=true}).
 */
public interface BiometricoDriver {

    /**
     * Prueba la conexión y devuelve datos del equipo para el diagnóstico
     * (plataforma, serie, cantidad de usuarios…).
     */
    Map<String, String> probarConexion(DispositivoBiometrico dispositivo);

    /**
     * Descarga TODOS los usuarios del equipo con sus templates.
     * Llama al progreso primero con la lista y después usuario por usuario.
     */
    List<UsuarioBiometrico> leerUsuarios(DispositivoBiometrico dispositivo, ProgresoBiometria progreso);

    /**
     * Carga masiva sistema → equipo: crea/actualiza cada usuario (PIN = RU)
     * con sus templates y avisa una línea por RU (progreso en vivo).
     * Por defecto no soportado (solo el intermediario Python lo implementa).
     */
    default void cargarUsuarios(DispositivoBiometrico dispositivo, List<UsuarioCarga> usuarios,
                                ProgresoCarga progreso) {
        throw new BiometriaException("La carga masiva necesita el intermediario Python (app.biometria.driver=python)");
    }
}
