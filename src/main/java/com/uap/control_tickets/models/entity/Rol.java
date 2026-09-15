package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Rol del sistema (ej. ADMINISTRADOR, CONTROL). Define QUE puede hacer un usuario.
 * La relacion Usuario <-> Rol es muchos-a-muchos y se resuelve con UsuarioRol.
 */
@Entity
@Table(name = "rol")
@Getter
@Setter
public class Rol extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRol;

    @Column(name = "nombre", nullable = false, unique = true, length = 50)
    private String nombre;
}
