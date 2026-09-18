package com.uap.control_tickets.dto.talonario;

import lombok.Getter;
import lombok.Setter;

/** Que cambio al reasignar: para avisarlo en pantalla sin tener que recargar y comparar. */
@Getter
@Setter
public class ResultadoAsignacionDto {

    private String vendedora;

    /** Talonarios que pasaron a estar a su cargo en esta operacion. */
    private int asignados;

    /** Talonarios que dejaron de estar a su cargo (quedaron sin asignar). */
    private int quitados;

    /** Total que le queda despues de la operacion. */
    private int total;
}
