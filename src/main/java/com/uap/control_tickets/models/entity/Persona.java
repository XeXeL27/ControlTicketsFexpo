package com.uap.control_tickets.models.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.uap.control_tickets.config.AuditoriaConfig;
import com.uap.control_tickets.enums.Genero;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Datos personales de una persona fisica.
 *
 * Se separa de "Usuario" a proposito: una Persona son los datos reales
 * (nombre, CI...), mientras que un Usuario es la cuenta de acceso
 * (username + password). Una persona puede o no tener cuenta.
 *
 * Nota: se quitaron correo, celular y fecha de nacimiento por pedido del
 * negocio (no son necesarios para el ticket).
 */
@Entity
@Table(name = "persona")
@Getter
@Setter
public class Persona extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPersona;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String paterno;

    @Column(length = 100)
    private String materno;

    @Column(nullable = false, unique = true, length = 20)
    private String ci;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private Genero genero;

    // Una persona puede tener varias cuentas de usuario (normalmente una).
    // @JsonIgnore evita bucles infinitos al serializar a JSON.
    @JsonIgnore
    @OneToMany(mappedBy = "persona", fetch = FetchType.LAZY)
    private List<Usuario> usuarios;

    /**
     * Campo calculado (no se guarda en BD): arma el nombre completo.
     * @Transient = JPA lo ignora como columna.
     */
    @Transient
    public String getNombreCompleto() {
        return String.format("%s %s %s",
                nombre  != null ? nombre  : "",
                paterno != null ? paterno : "",
                materno != null ? materno : "").trim();
    }
}
