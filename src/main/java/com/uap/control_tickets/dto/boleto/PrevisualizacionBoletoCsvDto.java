package com.uap.control_tickets.dto.boleto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Vista previa de la importación de boletos, ANTES de tocar la base.
 * Corre por el mismo camino que la importación real (misma detección de
 * codificación, separador y encabezado), así que lo que muestra es exactamente
 * lo que se va a guardar.
 */
@Getter
@Setter
public class PrevisualizacionBoletoCsvDto {

    private String codificacion;
    private String separador;
    private boolean encabezadoDetectado;
    private String encabezado;

    private int totalFilas;
    /** Códigos nuevos (se crean) y códigos que ya existen (se saltean, no se duplican). */
    private int nuevos;
    private int existentes;
    private int conProblemas;

    private List<FilaPrevia> filas = new ArrayList<>();

    @Getter
    @Setter
    public static class FilaPrevia {
        private int fila;
        private String codigo;
        /** Día en que vale el boleto (DIA_1/2/3). Vacío = falta en el archivo. */
        private String diaFeria;
        /** "NUEVO", "YA_EXISTE" o el motivo por el que fallaria. */
        private String estado;
    }
}
