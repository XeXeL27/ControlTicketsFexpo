package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Administrativo: una Persona con su codigo administrativo.
 * Datos del ticket de un administrativo: nombre completo + CI + codigo administrativo.
 */
@Entity
@Table(name = "administrativo")
@Getter
@Setter
public class Administrativo extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_administrativo")
    private Long idAdministrativo;

    @Column(name = "codigo_administrativo", nullable = false, unique = true, length = 30)
    private String codigoAdministrativo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", nullable = false)
    private Persona persona;
}
