package com.uap.control_tickets.dto.huella;

import lombok.Data;

/**
 * Lo que ve la barra de progreso: va por WebSocket (/topic/huellas/{jobId}) en
 * cada avance y por polling (GET /api/huellas/progreso) como respaldo.
 */
@Data
public class ProgresoHuellaDto {

    private Long jobId;
    /** EN_CURSO, FINALIZADO, ERROR, CANCELADO. */
    private String estado;
    /** BAJADA (equipo→sistema) o SUBIDA (carga masiva al equipo). */
    private String direccion;
    /** Etiqueta del alcance ("Portería, Bloque A" o "Carga carrera X → Portería"). */
    private String equipos;
    private int total;
    private int procesados;
    private int porcentaje;
    /** RU que se está procesando ahora (para el subtítulo del modal). */
    private String ruActual;
    private String equipoActual;

    private int correctos;
    private int duplicados;
    private int noEncontrados;
    private int sinHuella;
    private int errores;
    private String mensajeError;
}
