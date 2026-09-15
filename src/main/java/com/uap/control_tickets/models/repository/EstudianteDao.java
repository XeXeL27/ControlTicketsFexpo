package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstudianteDao extends JpaRepository<Estudiante, Long> {

    List<Estudiante> findAllByEstado(EstadoRegistro estado);

    Optional<Estudiante> findByRu(String ru);

    boolean existsByRu(String ru);

    boolean existsByRuAndIdEstudianteNot(String ru, Long idEstudiante);

    boolean existsByPersonaIdPersona(Long idPersona);

    // Respeta el borrado logico: sirve para saber si la persona ya figura como
    // estudiante ACTIVO, ignorando las filas eliminadas que se van a revivir.
    Optional<Estudiante> findByPersonaIdPersonaAndEstado(Long idPersona, EstadoRegistro estado);
}
