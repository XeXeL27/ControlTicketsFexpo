package com.uap.control_tickets.dto.huella;

import lombok.Data;

/**
 * Una huella guardada de un estudiante (sin los bytes del template, que pesan;
 * para ver/cruzar templates está la tabla huella_digital en BD).
 */
@Data
public class HuellaDigitalDto {

    private Long idHuella;
    /** Slot 0-9 que informó el biométrico. */
    private Integer dedo;
    /** Etiqueta legible ("Dedo 0" … "Dedo 9"). */
    private String nombreDedo;
    private String equipoOrigen;
    private String versionBiometrica;
    private String fechaCaptura;
    /** Tamaño aprox. del template en bytes (para verificar que no vino vacío). */
    private Integer tamanoBytes;
}
