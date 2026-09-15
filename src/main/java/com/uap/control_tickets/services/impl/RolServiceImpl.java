package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.rol.RolDetalleDto;
import com.uap.control_tickets.dto.rol.RolDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Rol;
import com.uap.control_tickets.models.repository.RolDao;
import com.uap.control_tickets.services.interfaces.RolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD de Rol. Los nombres se normalizan a MAYUSCULAS para que coincidan con "ROLE_XXX". */
@Service
@RequiredArgsConstructor
public class RolServiceImpl implements RolService {

    private final RolDao rolDao;

    @Override
    @Transactional(readOnly = true)
    public List<RolDetalleDto> listar() {
        return rolDao.findAllByEstado(EstadoRegistro.ACTIVO)
                .stream().map(this::toDetalleDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RolDetalleDto obtener(Long idRol) {
        return toDetalleDto(buscarActivo(idRol));
    }

    @Override
    @Transactional
    public RolDetalleDto crear(RolDto dto) {
        String nombre = dto.getNombre().trim().toUpperCase();
        if (rolDao.existsByNombre(nombre)) {
            throw new NegocioException("Ya existe el rol '" + nombre + "'");
        }
        Rol rol = new Rol();
        rol.setNombre(nombre);
        return toDetalleDto(rolDao.save(rol));
    }

    @Override
    @Transactional
    public RolDetalleDto actualizar(Long idRol, RolDto dto) {
        Rol rol = buscarActivo(idRol);
        String nombre = dto.getNombre().trim().toUpperCase();
        rolDao.findByNombre(nombre)
                .filter(r -> !r.getIdRol().equals(idRol))
                .ifPresent(r -> { throw new NegocioException("Ya existe el rol '" + nombre + "'"); });
        rol.setNombre(nombre);
        return toDetalleDto(rolDao.save(rol));
    }

    @Override
    @Transactional
    public void eliminar(Long idRol) {
        Rol rol = buscarActivo(idRol);
        rol.setEstado(EstadoRegistro.ELIMINADO);
        rolDao.save(rol);
    }

    private Rol buscarActivo(Long idRol) {
        return rolDao.findById(idRol)
                .filter(r -> r.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol no encontrado"));
    }

    private RolDetalleDto toDetalleDto(Rol rol) {
        RolDetalleDto dto = new RolDetalleDto();
        dto.setIdRol(rol.getIdRol());
        dto.setNombre(rol.getNombre());
        dto.setEstado(rol.getEstado().name());
        return dto;
    }
}
