package com.uap.control_tickets.dto.ticket;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de emitir tickets en lote.
 *
 * Mismo espiritu que ImportacionResultadoDto: la operacion NO se aborta por un
 * estudiante que falle; se informa fila por fila que paso con cada uno.
 */
@Getter
@Setter
public class EmisionMasivaDto {

    /** Estudiantes considerados en la operacion. */
    private int totalEstudiantes;

    /** Tickets nuevos realmente creados. */
    private int emitidos;

    /** Estudiantes que ya tenian ticket (no se duplica: la emision es idempotente). */
    private int omitidos;

    /** Los que no se pudieron emitir, con el motivo. */
    private List<ErrorEmision> errores = new ArrayList<>();

    public void agregarError(Long idEstudiante, String nombreCompleto, String motivo) {
        errores.add(new ErrorEmision(idEstudiante, nombreCompleto, motivo));
    }

    @Getter
    @Setter
    public static class ErrorEmision {
        private Long idEstudiante;
        private String nombreCompleto;
        private String motivo;

        public ErrorEmision(Long idEstudiante, String nombreCompleto, String motivo) {
            this.idEstudiante = idEstudiante;
            this.nombreCompleto = nombreCompleto;
            this.motivo = motivo;
        }
    }
}
