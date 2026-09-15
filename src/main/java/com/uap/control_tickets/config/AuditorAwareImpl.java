package com.uap.control_tickets.config;

import com.uap.control_tickets.models.entity.Usuario;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Le dice a Spring Data JPA QUIEN es el usuario actual, para llenar
 * automaticamente los campos @CreatedBy / @LastModifiedBy de AuditoriaConfig.
 *
 * Lee el usuario autenticado del SecurityContext (puesto ahi por el
 * JwtAuthenticationFilter). Si no hay nadie logueado (ej. durante el login
 * o el arranque), devuelve vacio.
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Usuario usuario) {
            return Optional.of(usuario.getIdUsuario());
        }

        return Optional.empty();
    }
}
