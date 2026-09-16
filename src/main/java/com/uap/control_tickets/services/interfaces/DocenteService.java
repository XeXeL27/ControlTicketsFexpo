package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.docente.DocenteDetalleDto;
import com.uap.control_tickets.dto.docente.DocenteDto;
import com.uap.control_tickets.dto.docente.PrevisualizacionDocCsvDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Contrato del CRUD de Docente + carga masiva por CSV. */
public interface DocenteService {
    List<DocenteDetalleDto> listar();
    DocenteDetalleDto obtener(Long idDocente);
    DocenteDetalleDto crear(DocenteDto dto);
    void eliminar(Long idDocente);

    /** Carga masiva desde CSV. Columnas: codigo docente, nombre completo, ci, carrera. */
    ImportacionResultadoDto importarCsv(MultipartFile archivo);

    /** Lee el CSV y cuenta qué pasaría, SIN escribir nada (para la vista previa). */
    PrevisualizacionDocCsvDto previsualizarCsv(MultipartFile archivo);
}
