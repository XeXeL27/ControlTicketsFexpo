package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.usuario.CambioPasswordDto;
import com.uap.control_tickets.dto.usuario.UsuarioDetalleDto;
import com.uap.control_tickets.dto.usuario.UsuarioDto;
import com.uap.control_tickets.dto.usuario.UsuarioRolDto;

import java.util.List;

/** Contrato del CRUD de Usuario y la gestion de sus roles. */
public interface UsuarioService {
    List<UsuarioDetalleDto> listar();
    UsuarioDetalleDto obtener(Long idUsuario);
    UsuarioDetalleDto miPerfil();
    UsuarioDetalleDto crear(UsuarioDto dto);
    UsuarioDetalleDto actualizar(Long idUsuario, UsuarioDto dto);
    void eliminar(Long idUsuario);

    // Gestion de estado y credenciales
    UsuarioDetalleDto cambiarBloqueo(Long idUsuario, boolean bloqueado);
    void cambiarPassword(Long idUsuario, CambioPasswordDto dto);

    // Gestion de roles del usuario
    UsuarioDetalleDto asignarRol(UsuarioRolDto dto);
    UsuarioDetalleDto quitarRol(UsuarioRolDto dto);
}
