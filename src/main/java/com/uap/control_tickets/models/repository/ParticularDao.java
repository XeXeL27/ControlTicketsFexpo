package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Particular;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParticularDao extends JpaRepository<Particular, Long> {

    List<Particular> findAllByEstado(EstadoRegistro estado);

    boolean existsByPersonaIdPersona(Long idPersona);
}
