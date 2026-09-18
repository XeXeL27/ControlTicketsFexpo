package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.huella.CargaMasivaDto;
import com.uap.control_tickets.dto.huella.HuellaDigitalDto;
import com.uap.control_tickets.dto.huella.ProgresoHuellaDto;
import com.uap.control_tickets.dto.huella.ResultadoHuellaDto;

import java.util.List;

/**
 * Contrato de la sincronización de huellas.
 *
 * {@link #iniciar} crea el job y lo dispara en segundo plano (@Async): vuelve
 * enseguida con el id para que la pantalla muestre la barra. El avance sale
 * por {@link #progreso} (polling) y por WebSocket /topic/huellas/{jobId}.
 */
public interface SincronizacionHuellaService {
    /** Crea el job EN_CURSO y lanza la tarea async. {@code idsEquipos} vacío = todos los activos. */
    Long iniciar(List<Long> idsEquipos);

    ProgresoHuellaDto progreso(Long jobId);

    /** Reporte final; {@code filtroEstado} = TODOS/CORRECTO/DUPLICADO/NO_ENCONTRADO/SIN_HUELLA/CARGADO/ACTUALIZADO/ERROR. */
    ResultadoHuellaDto resultado(Long jobId, String filtroEstado);

    /** Historial de sincronizaciones (más recientes primero). */
    List<ProgresoHuellaDto> historial();

    void cancelar(Long jobId);

    /**
     * Carga masiva sistema → equipo: crea el job SUBIDA y lo dispara en 2º
     * plano. Sube todos los estudiantes activos de la facultad/carrera
     * (PIN = RU, con sus huellas guardadas si tienen).
     */
    Long iniciarCarga(CargaMasivaDto dto);

    /** Las N huellas guardadas de un estudiante (qué dedo, de qué equipo, cuándo). */
    List<HuellaDigitalDto> huellasDeEstudiante(Long idEstudiante);
}
