package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /** Para la carga masiva al biométrico (comparación exacta). */
    List<Estudiante> findAllByEstadoAndFacultad(EstadoRegistro estado, String facultad);

    List<Estudiante> findAllByEstadoAndCarrera(EstadoRegistro estado, String carrera);

    @Query("select distinct e.facultad from Estudiante e where e.estado = :estado and e.facultad is not null order by e.facultad")
    List<String> facultadesDistintas(EstadoRegistro estado);

    @Query("select distinct e.carrera from Estudiante e where e.estado = :estado and e.carrera is not null order by e.carrera")
    List<String> carrerasDistintas(EstadoRegistro estado);

    /** Cuántos del grupo tienen al menos una huella (la carga solo sube esos). */
    @Query("select count(e) from Estudiante e where e.estado = :estado and e.facultad = :valor "
            + "and exists (select 1 from HuellaDigital h where h.estudiante = e and h.estado = :estado)")
    long contarConHuellaPorFacultad(EstadoRegistro estado, String valor);

    @Query("select count(e) from Estudiante e where e.estado = :estado and e.carrera = :valor "
            + "and exists (select 1 from HuellaDigital h where h.estudiante = e and h.estado = :estado)")
    long contarConHuellaPorCarrera(EstadoRegistro estado, String valor);

    /** Ids de persona con vinculo activo. Una sola consulta para clasificar el listado. */
    @org.springframework.data.jpa.repository.Query(
            "select e.persona.idPersona from Estudiante e where e.estado = :estado")
    java.util.List<Long> idsPersonaActivas(
            @org.springframework.data.repository.query.Param("estado") EstadoRegistro estado);
}
