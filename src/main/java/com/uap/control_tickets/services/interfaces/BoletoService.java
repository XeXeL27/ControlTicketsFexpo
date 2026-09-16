package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.boleto.BoletoDetalleDto;
import com.uap.control_tickets.dto.boleto.BoletoDto;
import com.uap.control_tickets.dto.boleto.PrevisualizacionBoletoCsvDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Contrato del CRUD de Boleto (boletos de venta de la feria) + carga masiva por CSV. */
public interface BoletoService {
    List<BoletoDetalleDto> listar();
    BoletoDetalleDto obtener(Long idBoleto);
    BoletoDetalleDto crear(BoletoDto dto);
    void eliminar(Long idBoleto);

    /** Carga masiva desde CSV: un código de boleto por fila. */
    ImportacionResultadoDto importarCsv(MultipartFile archivo);

    /** Lee el CSV y cuenta qué pasaría, SIN escribir nada (para la vista previa). */
    PrevisualizacionBoletoCsvDto previsualizarCsv(MultipartFile archivo);
}
