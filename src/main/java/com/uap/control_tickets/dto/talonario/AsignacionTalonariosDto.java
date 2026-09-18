package com.uap.control_tickets.dto.talonario;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Reasigna DE UNA VEZ todos los talonarios de una vendedora.
 *
 * La lista es el estado final, no un agregado: los talonarios que vienen quedan
 * asignados a {@code idUsuario} y los que HOY tiene esa persona y no vienen en la
 * lista quedan sin asignar. Asi la pantalla es una sola lista de casillas
 * ("estos son los talonarios de Fulana") en vez de dos acciones separadas de
 * asignar y quitar, que es donde se cometen errores.
 *
 * Los talonarios pueden ser de CUALQUIER destino y evento: una vendedora puede
 * tener a la vez de parqueo, concierto y feria. No hay ninguna regla que lo impida.
 */
@Getter
@Setter
public class AsignacionTalonariosDto {

    @NotNull(message = "Indique la vendedora")
    private Long idUsuario;

    /** Ids de los talonarios que quedan a su cargo. Vacia = se le quitan todos. */
    private List<Long> idTalonarios;
}
