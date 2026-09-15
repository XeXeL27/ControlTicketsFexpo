package com.uap.control_tickets.config.security;

import com.uap.control_tickets.models.entity.Usuario;
import com.uap.control_tickets.models.repository.UsuarioDao;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que se ejecuta UNA vez por cada peticion HTTP, ANTES de llegar al
 * controller. Su trabajo: leer el token del header "Authorization: Bearer XXX",
 * validarlo y, si es correcto, registrar al usuario en el SecurityContext para
 * que el resto de la app sepa quien esta pidiendo.
 *
 * Si no hay token o es invalido, simplemente deja pasar la peticion SIN
 * autenticar; luego SecurityConfig decide si esa ruta exigia login o no.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioDao usuarioDao;

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Leer el header Authorization
        final String authHeader = request.getHeader("Authorization");

        // 2. Sin token o formato incorrecto -> seguir sin autenticar
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Quitar el prefijo "Bearer "
        final String token = authHeader.substring(7);

        // 4. Validar firma y expiracion
        if (!jwtService.validarToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 5. Si ya hay autenticacion en el contexto, no repetir
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 6. Cargar el usuario desde BD (fuente de verdad, no confiamos solo en el token)
        final String username = jwtService.extraerUsername(token);
        Usuario usuario = usuarioDao.findByUsername(username).orElse(null);

        if (usuario == null || !usuario.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // 7. Registrar al usuario autenticado en el SecurityContext
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        usuario, null, usuario.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // 8. Continuar la cadena
        filterChain.doFilter(request, response);
    }
}
