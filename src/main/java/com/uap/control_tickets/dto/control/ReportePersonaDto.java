package com.uap.control_tickets.dto.control;

import lombok.Data;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Totales históricos por persona y estado de sus tickets activos. */
@Data
public class ReportePersonaDto {
    private Long idPersona;
    private String nombreCompleto;
    private String ci;
    private List<String> categorias = new ArrayList<>();
    private List<String> codigos = new ArrayList<>();
    private long entradas;
    private long salidas;
    private boolean dentro;
    private Instant ultimoMovimiento;
}
