package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Docente: una Persona con su codigo docente.
 * Datos del ticket de un docente: nombre completo + CI + codigo docente.
 * Mismo patron que Administrativo.
 */
@Entity
@Table(name = "docente")
@Getter
@Setter
public class Docente extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_docente")
    private Long idDocente;

    @Column(name = "codigo_docente", nullable = false, unique = true, length = 30)
    private String codigoDocente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", nullable = false)
    private Persona persona;
}
