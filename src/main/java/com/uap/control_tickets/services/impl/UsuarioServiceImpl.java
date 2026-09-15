package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.usuario.CambioPasswordDto;
import com.uap.control_tickets.dto.usuario.UsuarioDetalleDto;
import com.uap.control_tickets.dto.usuario.UsuarioDto;
import com.uap.control_tickets.dto.usuario.UsuarioRolDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Persona;
import com.uap.control_tickets.models.entity.Rol;
import com.uap.control_tickets.models.entity.Usuario;
import com.uap.control_tickets.models.entity.UsuarioRol;
import com.uap.control_tickets.models.repository.PersonaDao;
import com.uap.control_tickets.models.repository.RolDao;
import com.uap.control_tickets.models.repository.UsuarioDao;
import com.uap.control_tickets.models.repository.UsuarioRolDao;
import com.uap.control_tickets.services.interfaces.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD de Usuario + gestion de roles.
 *
 * Puntos importantes:
 *  - El password SIEMPRE se guarda hasheado con BCrypt (passwordEncoder.encode).
 *  - Un usuario se enlaza a una Persona existente (idPersona).
 *  - Los roles se asignan/quitan como filas UsuarioRol (borrado logico).
 *  - "eliminar" es logico: no borra la fila, marca estado = ELIMINADO.
 */
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioDao usuarioDao;
    private final PersonaDao personaDao;
    private final RolDao rolDao;
    private final UsuarioRolDao usuarioRolDao;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioDetalleDto> listar() {
        return usuarioDao.findAllByEstado(EstadoRegistro.ACTIVO)
                .stream().map(this::toDetalleDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioDetalleDto obtener(Long idUsuario) {
        return toDetalleDto(buscarActivo(idUsuario));
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioDetalleDto miPerfil() {
        Usuario actual = (Usuario) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        // Recargamos desde BD para traer roles/persona frescos.
        return toDetalleDto(buscarActivo(actual.getIdUsuario()));
    }

    @Override
    @Transactional
    public UsuarioDetalleDto crear(UsuarioDto dto) {
        if (usuarioDao.existsByUsername(dto.getUsername().trim())) {
            throw new NegocioException("El username '" + dto.getUsername() + "' ya esta en uso");
        }
        Persona persona = personaDao.findById(dto.getIdPersona())
                .filter(p -> p.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Persona no encontrada"));

        Usuario usuario = new Usuario();
        usuario.setUsername(dto.getUsername().trim());
        usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        usuario.setPersona(persona);
        return toDetalleDto(usuarioDao.save(usuario));
    }

    @Override
    @Transactional
    public UsuarioDetalleDto actualizar(Long idUsuario, UsuarioDto dto) {
        Usuario usuario = buscarActivo(idUsuario);

        if (usuarioDao.existsByUsernameAndIdUsuarioNot(dto.getUsername().trim(), idUsuario)) {
            throw new NegocioException("El username '" + dto.getUsername() + "' ya esta en uso");
        }
        usuario.setUsername(dto.getUsername().trim());

        // Solo cambia el password si mandaron uno nuevo (no vacio).
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        // Cambio de persona asociada (opcional).
        if (dto.getIdPersona() != null
                && !dto.getIdPersona().equals(usuario.getPersona().getIdPersona())) {
            Persona persona = personaDao.findById(dto.getIdPersona())
                    .filter(p -> p.getEstado() == EstadoRegistro.ACTIVO)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Persona no encontrada"));
            usuario.setPersona(persona);
        }
        return toDetalleDto(usuarioDao.save(usuario));
    }

    @Override
    @Transactional
    public void eliminar(Long idUsuario) {
        Usuario usuario = buscarActivo(idUsuario);
        usuario.setEstado(EstadoRegistro.ELIMINADO);
        usuarioDao.save(usuario);
    }

    @Override
    @Transactional
    public UsuarioDetalleDto cambiarBloqueo(Long idUsuario, boolean bloqueado) {
        Usuario usuario = buscarActivo(idUsuario);
        usuario.setBloqueado(bloqueado);
        return toDetalleDto(usuarioDao.save(usuario));
    }

    @Override
    @Transactional
    public void cambiarPassword(Long idUsuario, CambioPasswordDto dto) {
        Usuario usuario = buscarActivo(idUsuario);
        usuario.setPassword(passwordEncoder.encode(dto.getNuevaPassword()));
        usuarioDao.save(usuario);
    }

    @Override
    @Transactional
    public UsuarioDetalleDto asignarRol(UsuarioRolDto dto) {
        Usuario usuario = buscarActivo(dto.getIdUsuario());
        Rol rol = rolDao.findById(dto.getIdRol())
                .filter(r -> r.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol no encontrado"));

        // Evita duplicar un rol ya asignado y activo.
        boolean yaTiene = usuarioRolDao
                .findByUsuarioIdUsuarioAndRolIdRolAndEstado(
                        usuario.getIdUsuario(), rol.getIdRol(), EstadoRegistro.ACTIVO)
                .isPresent();
        if (yaTiene) {
            throw new NegocioException("El usuario ya tiene el rol '" + rol.getNombre() + "'");
        }

        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setUsuario(usuario);
        usuarioRol.setRol(rol);
        usuarioRolDao.save(usuarioRol);

        return toDetalleDto(buscarActivo(usuario.getIdUsuario()));
    }

    @Override
    @Transactional
    public UsuarioDetalleDto quitarRol(UsuarioRolDto dto) {
        UsuarioRol usuarioRol = usuarioRolDao
                .findByUsuarioIdUsuarioAndRolIdRolAndEstado(
                        dto.getIdUsuario(), dto.getIdRol(), EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El usuario no tiene ese rol asignado"));
        usuarioRol.setEstado(EstadoRegistro.ELIMINADO);
        usuarioRolDao.save(usuarioRol);
        return toDetalleDto(buscarActivo(dto.getIdUsuario()));
    }

    // ---------------- helpers ----------------

    private Usuario buscarActivo(Long idUsuario) {
        return usuarioDao.findById(idUsuario)
                .filter(u -> u.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private UsuarioDetalleDto toDetalleDto(Usuario u) {
        UsuarioDetalleDto dto = new UsuarioDetalleDto();
        dto.setIdUsuario(u.getIdUsuario());
        dto.setUsername(u.getUsername());
        dto.setBloqueado(u.isBloqueado());
        dto.setEstado(u.getEstado().name());

        Persona p = u.getPersona();
        if (p != null) {
            dto.setIdPersona(p.getIdPersona());
            dto.setNombreCompleto(p.getNombreCompleto());
            dto.setCi(p.getCi());
        }
        // Nombres de roles activos, sin el prefijo ROLE_.
        dto.setRoles(u.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .toList());
        return dto;
    }
}
