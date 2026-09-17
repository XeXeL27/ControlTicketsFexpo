package com.uap.control_tickets.services.biometrico;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un usuario tal como vive dentro del biométrico.
 *
 * El {@code pin} ES el RU (así se enrolan en el equipo). Los templates van por
 * dedo (0-9) en Base64, tal como los entrega el equipo (SSR_GetUserTmpStr).
 */
@Data
public class UsuarioBiometrico {

    /** PIN del equipo = RU del estudiante. */
    private String pin;

    /** Nombre cargado en el equipo (informativo, puede venir vacío). */
    private String nombre;

    /** Nº de usuario interno del equipo (uid clásico, -1 si SSR no lo dio). */
    private int uid = -1;

    /** Dedo (0-9) → template Base64. Vacío = sin huella enrolada. */
    private Map<Integer, String> templates = new LinkedHashMap<>();

    /** Versión biométrica que reportó el equipo, si la informó. */
    private String versionBiometrica;

    public UsuarioBiometrico(String pin) {
        this.pin = pin;
    }

    public boolean tieneHuellas() {
        return !templates.isEmpty();
    }
}
