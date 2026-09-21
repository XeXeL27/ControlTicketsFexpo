package com.uap.control_tickets.dto.talonario;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Qué pasó al regularizar: cuántos quedaron vendidos a nombre del responsable. */
@Getter
@Setter
public class ResultadoRegularizacionDto {
    private int solicitados;
    private int cambiados;
    /** Ya estaban VENDIDOS a ese mismo responsable en esa misma fecha. */
    private int sinCambios;
    private List<String> avisos = new ArrayList<>();
    /** Username del responsable al que quedaron las ventas. null = sin responsable. */
    private String responsable;
    private Instant fechaVenta;

    public void aviso(String texto) {
        avisos.add(texto);
    }
}
