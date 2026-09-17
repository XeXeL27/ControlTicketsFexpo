package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.huella.DispositivoBiometricoDetalleDto;
import com.uap.control_tickets.dto.huella.DispositivoBiometricoDto;

import java.util.List;
import java.util.Map;

/** Contrato del ABM de biométricos + prueba de conexión al equipo. */
public interface DispositivoBiometricoService {
    List<DispositivoBiometricoDetalleDto> listar();

    DispositivoBiometricoDetalleDto obtener(Long idDispositivo);

    DispositivoBiometricoDetalleDto crear(DispositivoBiometricoDto dto);

    DispositivoBiometricoDetalleDto actualizar(Long idDispositivo, DispositivoBiometricoDto dto);

    void eliminar(Long idDispositivo);

    /** Conecta al equipo y devuelve sus datos (o lanza NegocioException con el motivo). */
    Map<String, String> probarConexion(Long idDispositivo);
}
