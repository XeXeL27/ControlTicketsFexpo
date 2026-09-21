package com.uap.control_tickets.dto.talonario;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

/**
 * Regulariza ventas de un talonario: marca boletos como VENDIDOS a nombre de
 * un responsable y con una fecha dados (no necesariamente el admin que lo
 * carga ni hoy).
 *
 * Es la corrección del marcado normal: la vendedora a veces vende y rinde
 * después, o rinde a otro nombre. Acá el admin deja constancia de quién
 * vendió realmente y cuándo. Quién hizo la regularización queda en la
 * auditoría (_modificacion_id_usuario).
 *
 * Formas de indicar los números (igual que el marcado): hastaNumero
 * ("vendidos hasta el 137"), desde + hasta (un rango) o numeros sueltos.
 */
@Data
public class RegularizacionVentaDto {

    @NotNull(message = "Indique el talonario")
    private Long idTalonario;

    /** Quién vendió realmente esos boletos (el responsable). Opcional: sin
     * responsable solo se corrige la fecha y se conserva el vendedor que
     * ya figura (o queda sin vendedor si estaba disponible). */
    private Long idResponsable;

    /** Cuándo se vendieron (no puede ser futura). */
    @NotNull(message = "Indique la fecha de la venta")
    private Instant fechaVenta;

    /** Marca del primer número del talonario hasta este (inclusive). */
    private Integer hastaNumero;

    private Integer desde;
    private Integer hasta;

    /** Números sueltos (uno por uno). */
    private java.util.List<Integer> numeros;
}
