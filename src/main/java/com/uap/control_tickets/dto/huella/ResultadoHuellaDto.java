package com.uap.control_tickets.dto.huella;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Reporte final de una sincronización: resumen + detalle por RU.
 *
 * El detalle puede ser miles de filas, así que el endpoint acepta
 * ?estado=CORRECTO|DUPLICADO|NO_ENCONTRADO|SIN_HUELLA|ERROR|TODOS
 * (por defecto trae todo salvo CORRECTO, que suele ser lo voluminoso y lo
 * menos interesante; el conteo de correctos igual viene en el resumen).
 */
@Data
public class ResultadoHuellaDto {

    private Long jobId;
    private String estado;
    private String equipos;
    private int total;
    private int correctos;
    private int duplicados;
    private int noEncontrados;
    private int sinHuella;
    private int errores;
    private String mensajeError;

    private List<DetalleHuellaDto> detalles = new ArrayList<>();
    /** Si se recortó el detalle por el filtro ?estado=, acá dice cuál. */
    private String filtroEstado;

    @Data
    public static class DetalleHuellaDto {
        private String ru;
        private String equipo;
        /** CORRECTO, DUPLICADO, NO_ENCONTRADO, SIN_HUELLA, ERROR. */
        private String estado;
        private String mensaje;
    }
}
