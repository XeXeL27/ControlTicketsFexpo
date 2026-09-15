package com.uap.control_tickets.config.security;

import com.uap.control_tickets.models.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Metodos reutilizables para las expresiones @PreAuthorize de los controllers.
 *
 * Regla de oro: siempre resuelve la identidad desde el usuario autenticado en
 * el SecurityContext, NUNCA desde datos que envia el cliente. Asi un usuario no
 * puede hacerse pasar por otro cambiando un id en la peticion.
 *
 * Uso en un controller:
 *   @PreAuthorize("hasRole('ADMINISTRADOR') or @autorizacionService.esUsuarioActual(#idUsuario)")
 */
@Component("autorizacionService")
@RequiredArgsConstructor
public class AutorizacionService {

    /** Usuario logueado, tomado del contexto de seguridad. */
    private Usuario usuarioActual() {
        return (Usuario) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    public Long idUsuarioActual() {
        return usuarioActual().getIdUsuario();
    }

    /** ¿El id recibido corresponde al propio usuario logueado? */
    public boolean esUsuarioActual(Long idUsuario) {
        return idUsuario != null && idUsuario.equals(idUsuarioActual());
    }

    /** ¿El id de persona recibido es el de la persona del usuario logueado? */
    public boolean esPersonaActual(Long idPersona) {
        return idPersona != null
                && usuarioActual().getPersona() != null
                && idPersona.equals(usuarioActual().getPersona().getIdPersona());
    }
}
