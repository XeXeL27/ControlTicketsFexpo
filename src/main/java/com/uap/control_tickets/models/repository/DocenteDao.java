package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Docente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocenteDao extends JpaRepository<Docente, Long> {

    List<Docente> findAllByEstado(EstadoRegistro estado);

    Optional<Docente> findByCodigoDocente(String codigoDocente);

    Optional<Docente> findByPersonaIdPersonaAndEstado(Long idPersona, EstadoRegistro estado);

    boolean existsByCodigoDocente(String codigoDocente);

    boolean existsByCodigoDocenteAndIdDocenteNot(String codigo, Long idDocente);

    boolean existsByPersonaIdPersona(Long idPersona);
}
