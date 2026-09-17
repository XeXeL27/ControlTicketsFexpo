package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.HuellaDigital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HuellaDigitalDao extends JpaRepository<HuellaDigital, Long> {

    List<HuellaDigital> findAllByEstudianteIdEstudianteAndEstado(Long idEstudiante, EstadoRegistro estado);

    Optional<HuellaDigital> findByEstudianteIdEstudianteAndDedoAndEstado(
            Long idEstudiante, Integer dedo, EstadoRegistro estado);

    boolean existsByEstudianteIdEstudianteAndEstado(Long idEstudiante, EstadoRegistro estado);

    long countByEstudianteIdEstudianteAndEstado(Long idEstudiante, EstadoRegistro estado);

    /** Para detectar el mismo template enrolado en dos RUs distintos. */
    List<HuellaDigital> findAllByTemplateAndEstado(String template, EstadoRegistro estado);
}
