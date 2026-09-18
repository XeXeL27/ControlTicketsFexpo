package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.estudiante.EstudianteDetalleDto;
import com.uap.control_tickets.dto.estudiante.EstudianteDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.dto.estudiante.PrevisualizacionCsvDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Contrato del CRUD de Estudiante + carga masiva por CSV. */
public interface EstudianteService {
    List<EstudianteDetalleDto> listar();
    EstudianteDetalleDto obtener(Long idEstudiante);
    EstudianteDetalleDto crear(EstudianteDto dto);
    void eliminar(Long idEstudiante);

    /** Carga masiva desde un archivo CSV (nombre,paterno,materno,ci,ru,facultad,carrera). */
    ImportacionResultadoDto importarCsv(MultipartFile archivo);

    /** Lee el CSV y reporta que pasaria al importarlo, sin escribir en la base. */
    PrevisualizacionCsvDto previsualizarCsv(MultipartFile archivo);

    /** Facultades y carreras distintas (para la carga masiva al biométrico). */
    List<String> facultades();

    List<String> carreras();
}
