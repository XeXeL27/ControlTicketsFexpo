package com.uap.control_tickets.dto.control;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Registro previo de un boleto, para mostrarlo al reingresar. */
@Getter
@Setter
public class RegistroSalidaDetalleDto {
    private Long idRegistro;
    private String nombre;
    private String ci;
    /**
     * true = hay foto guardada. La imagen NO viaja en el JSON: se pide aparte a
     * `GET /api/control/boletos/registro-salida/foto?idRegistro=`, igual que los
     * PNG/PDF de los tickets, para no inflar cada respuesta del escaner.
     */
    private boolean tieneFoto;
    private boolean sinDatos;
    /** Cuándo se tomaron los datos (la salida anterior). */
    private Instant fecha;
    /** Nombre de usuario del control que lo registró. */
    private String registradoPor;
}
