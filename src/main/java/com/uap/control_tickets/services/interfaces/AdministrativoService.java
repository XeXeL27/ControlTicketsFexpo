package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.administrativo.AdministrativoDetalleDto;
import com.uap.control_tickets.dto.administrativo.AdministrativoDto;
import com.uap.control_tickets.dto.administrativo.PrevisualizacionAdmCsvDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Contrato del CRUD de Administrativo + carga masiva por CSV. */
public interface AdministrativoService {
    List<AdministrativoDetalleDto> listar();
    AdministrativoDetalleDto obtener(Long idAdministrativo);
    AdministrativoDetalleDto crear(AdministrativoDto dto);
    void eliminar(Long idAdministrativo);

    /** Carga masiva desde CSV. Columnas: codigo administrativo, nombre completo, ci. */
    ImportacionResultadoDto importarCsv(MultipartFile archivo);

    /** Lee el CSV y cuenta qué pasaría, SIN escribir nada (para la vista previa). */
    PrevisualizacionAdmCsvDto previsualizarCsv(MultipartFile archivo);
}
