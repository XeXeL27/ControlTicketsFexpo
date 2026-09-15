package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla intermedia que une Usuario con Rol (muchos-a-muchos).
 * Cada fila = "este usuario tiene este rol". Hereda de AuditoriaConfig,
 * asi un rol se puede "quitar" marcandolo como ELIMINADO sin borrar la fila.
 */
@Entity
@Table(name = "usuario_rol")
@Getter
@Setter
public class UsuarioRol extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idUsuarioRol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;
}
