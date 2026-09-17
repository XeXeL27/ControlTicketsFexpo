package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Docente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocenteDao extends JpaRepository<Docente, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Docente e where e.idDocente = :id")
    Optional<Docente> buscarParaCambio(@org.springframework.data.repository.query.Param("id") Long id);


    List<Docente> findAllByEstado(EstadoRegistro estado);

    Optional<Docente> findByCodigoDocente(String codigoDocente);

    Optional<Docente> findByPersonaIdPersonaAndEstado(Long idPersona, EstadoRegistro estado);

    boolean existsByCodigoDocente(String codigoDocente);

    boolean existsByCodigoDocenteAndIdDocenteNot(String codigo, Long idDocente);

    boolean existsByPersonaIdPersona(Long idPersona);

    /** Ids de persona con vinculo activo. Una sola consulta para clasificar el listado. */
    @org.springframework.data.jpa.repository.Query(
            "select e.persona.idPersona from Docente e where e.estado = :estado")
    java.util.List<Long> idsPersonaActivas(
            @org.springframework.data.repository.query.Param("estado") EstadoRegistro estado);
}
