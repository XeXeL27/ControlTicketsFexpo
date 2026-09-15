package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RolDao extends JpaRepository<Rol, Long> {

    Optional<Rol> findByNombre(String nombre);

    List<Rol> findAllByEstado(EstadoRegistro estado);

    boolean existsByNombre(String nombre);
}
