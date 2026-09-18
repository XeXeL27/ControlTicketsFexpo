package com.uap.control_tickets.services.biometrico;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Elige con qué driver se habla a los equipos.
 *
 * {@code app.biometria.simulacion=true} → datos inventados (sin red).
 * Si no, {@code app.biometria.driver}: {@code python} (pyzk como
 * intermediario, default) o {@code java} (driver TCP propio, respaldo).
 */
@Component
@RequiredArgsConstructor
public class BiometricoDriverSelector {

    private final ZktecoTcpDriver java;
    private final PythonZktecoDriver python;
    private final SimulacionBiometricoDriver simulado;

    @Value("${app.biometria.simulacion:false}")
    private boolean simulacion;

    @Value("${app.biometria.driver:python}")
    private String driver;

    public BiometricoDriver actual() {
        if (simulacion) return simulado;
        if ("java".equalsIgnoreCase(driver)) return java;
        return python;
    }
}
