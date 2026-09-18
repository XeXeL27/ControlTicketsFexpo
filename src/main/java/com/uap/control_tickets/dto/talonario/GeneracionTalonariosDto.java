package com.uap.control_tickets.dto.talonario;

import com.uap.control_tickets.enums.TipoTalonario;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Alta de VARIOS talonarios seguidos de un mismo tipo.
 * Ej: 20 talonarios de 200 desde el 1 -> A(1-200), B(201-400)... sin huecos.
 * Evita cargar veinte formularios a mano y equivocarse en un rango.
 */
@Data
public class GeneracionTalonariosDto {

    @NotNull(message = "Indique el tipo (evento 1/2/3 o combo)")
    private TipoTalonario tipo;

    @NotNull @Min(value = 1, message = "Debe generar al menos 1 talonario")
    @Max(value = 500, message = "Máximo 500 talonarios por operación")
    private Integer cantidadTalonarios;

    @NotNull @Min(value = 1, message = "Cada talonario debe tener al menos 1 boleto")
    @Max(value = 10000, message = "Máximo 10000 boletos por talonario")
    private Integer boletosPorTalonario;

    /** Desde qué número arranca el primero. Si va null, sigue al último del tipo. */
    private Integer numeroInicial;

    /** Prefijo del nombre: "Talonario" -> "Talonario 1", "Talonario 2"... */
    private String prefijoNombre;

    private BigDecimal precioUnitario;
}
