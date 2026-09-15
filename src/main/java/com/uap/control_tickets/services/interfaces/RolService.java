package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.rol.RolDetalleDto;
import com.uap.control_tickets.dto.rol.RolDto;

import java.util.List;

/** Contrato del CRUD de Rol. */
public interface RolService {
    List<RolDetalleDto> listar();
    RolDetalleDto obtener(Long idRol);
    RolDetalleDto crear(RolDto dto);
    RolDetalleDto actualizar(Long idRol, RolDto dto);
    void eliminar(Long idRol);
}
