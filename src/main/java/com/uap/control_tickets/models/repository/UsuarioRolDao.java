package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.UsuarioRol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRolDao extends JpaRepository<UsuarioRol, Long> {

    List<UsuarioRol> findAllByUsuarioIdUsuarioAndEstado(Long idUsuario, EstadoRegistro estado);

    Optional<UsuarioRol> findByUsuarioIdUsuarioAndRolIdRolAndEstado(
            Long idUsuario, Long idRol, EstadoRegistro estado);
}
