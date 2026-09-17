package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Estudiante: una Persona con datos academicos.
 *
 * Sigue el patron de escuela-tecnica: los datos personales (nombre completo, CI)
 * viven en Persona; aqui van solo los campos propios del estudiante.
 * Datos del ticket de un estudiante: nombre completo + RU + CI + carrera/facultad.
 */
@Entity
@Table(name = "estudiante")
@Getter
@Setter
public class Estudiante extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estudiante")
    private Long idEstudiante;

    /** Registro Universitario, unico por estudiante. */
    @Column(name = "ru", nullable = false, unique = true, length = 30)
    private String ru;

    @Column(name = "facultad", length = 150)
    private String facultad;

    @Column(name = "carrera", length = 150)
    private String carrera;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", nullable = false)
    private Persona persona;

    /**
     * Flag rápido de "con huella": se prende al guardar el primer template en
     * huella_digital. Evita un join/EXISTS para filtrar en las pantallas.
     */
    @Column(name = "tiene_huella", nullable = false)
    private Boolean tieneHuella = false;

    @Column(name = "fecha_huella")
    private Instant fechaHuella;
}
