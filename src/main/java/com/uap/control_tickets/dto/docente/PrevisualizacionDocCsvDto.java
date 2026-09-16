package com.uap.control_tickets.dto.docente;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Vista previa de la importación de docentes, ANTES de tocar la base.
 *
 * Corre por el mismo camino que la importación real (misma detección de
 * codificación, separador y encabezado), así que lo que muestra es exactamente lo
 * que se va a guardar. Sirve para detectar a tiempo un CSV mal codificado.
 * Equivale al PrevisualizacionCsvDto de estudiantes, con las columnas de
 * docente (código docente en vez de R.U./carrera).
 */
@Getter
@Setter
public class PrevisualizacionDocCsvDto {

    /** Codificacion con la que se leyo el archivo (UTF-8, windows-1252, IBM850). */
    private String codificacion;

    /** Separador detectado: "," o ";". */
    private String separador;

    /** true = la primera fila era un encabezado y se salteo. */
    private boolean encabezadoDetectado;

    /** Texto de la fila de encabezado, si la hubo. */
    private String encabezado;

    /** Filas de datos del archivo (sin contar el encabezado). */
    private int totalFilas;

    /** De esas, cuantas son altas nuevas y cuantas actualizarian a alguien ya cargado. */
    private int nuevos;
    private int existentes;

    /** Cuantas filas traen algun problema (falta el codigo o el CI). */
    private int conProblemas;

    /** Las primeras filas, ya parseadas, para mostrar en pantalla. */
    private List<FilaPrevia> filas = new ArrayList<>();

    @Getter
    @Setter
    public static class FilaPrevia {
        private int fila;
        private String codigoDocente;
        private String carrera;
        private String nombreCompleto;
        private String ci;
        /** "NUEVO", "ACTUALIZA" o el motivo por el que fallaria. */
        private String estado;
        /** Aviso no bloqueante (ej. texto con pinta de mal codificado). */
        private String advertencia;
    }
}
