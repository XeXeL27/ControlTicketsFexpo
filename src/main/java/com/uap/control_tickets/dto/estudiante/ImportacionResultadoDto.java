package com.uap.control_tickets.dto.estudiante;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Resumen del resultado de una importación masiva por CSV.
 * Reporta cuántas filas se procesaron, cuántas se crearon, cuántas se actualizaron
 * (el RU ya existía: volver a subir el padrón corrige los datos) y el detalle de
 * las que fallaron, para que el usuario corrija su archivo.
 */
@Data
public class ImportacionResultadoDto {

    private int totalFilas;
    /** Estudiantes que no existian y se dieron de alta. */
    private int creados;
    /** Estudiantes cuyo RU ya existia y se actualizaron con los datos del archivo. */
    private int actualizados;
    private List<ErrorFila> errores = new ArrayList<>();

    public void agregarError(int fila, String motivo) {
        errores.add(new ErrorFila(fila, motivo));
    }

    @Data
    public static class ErrorFila {
        private final int fila;      // número de fila del CSV (1 = primera fila de datos)
        private final String motivo;
    }
}
