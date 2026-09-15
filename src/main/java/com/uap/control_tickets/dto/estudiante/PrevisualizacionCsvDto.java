package com.uap.control_tickets.dto.estudiante;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se vera al importar, ANTES de tocar la base.
 *
 * La previsualizacion corre por el mismo camino que la importacion real
 * (misma deteccion de codificacion, de separador y de encabezado), asi que lo que
 * muestra es exactamente lo que se va a guardar. Sirve sobre todo para detectar a
 * tiempo un archivo mal codificado: si en la vista previa se ven "Ingenier¡a" o
 * simbolos raros, conviene reexportar el CSV antes de importarlo.
 */
@Getter
@Setter
public class PrevisualizacionCsvDto {

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

    /** Cuantas filas traen algun problema (falta el RU o el CI). */
    private int conProblemas;

    /** Las primeras filas, ya parseadas, para mostrar en pantalla. */
    private List<FilaPrevia> filas = new ArrayList<>();

    @Getter
    @Setter
    public static class FilaPrevia {
        private int fila;
        private String ru;
        private String nombreCompleto;
        private String ci;
        private String carrera;
        /** "NUEVO", "ACTUALIZA" o el motivo por el que fallaria. */
        private String estado;
        /** Aviso no bloqueante (ej. texto con pinta de mal codificado). */
        private String advertencia;
    }
}
