package com.uap.control_tickets.models.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cuenta de acceso al sistema.
 *
 * Implementa UserDetails (interfaz de Spring Security): asi Spring sabe leer
 * el username, el password (hash BCrypt) y los roles directamente de esta
 * entidad para autenticar y autorizar. El password NUNCA sale en el JSON
 * gracias a @JsonIgnore.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario extends AuditoriaConfig implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idUsuario;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean bloqueado = false;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "id_persona", nullable = false)
    private Persona persona;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<UsuarioRol> usuarioRoles;

    // ---------------- UserDetails (contrato de Spring Security) ----------------

    /**
     * Roles del usuario en el formato que espera Spring Security: "ROLE_XXX".
     * Solo cuenta los roles ACTIVOS (no los quitados/ELIMINADO).
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (usuarioRoles == null) return List.of();
        return usuarioRoles.stream()
                .filter(ur -> ur.getEstado().name().equals("ACTIVO"))
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRol().getNombre()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return !this.bloqueado; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    /** El usuario puede loguearse solo si esta ACTIVO y no bloqueado. */
    @Override
    public boolean isEnabled() {
        return this.getEstado().name().equals("ACTIVO") && !this.bloqueado;
    }
}
