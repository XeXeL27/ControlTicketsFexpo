package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.config.security.JwtService;
import com.uap.control_tickets.dto.login.LoginDto;
import com.uap.control_tickets.dto.login.TokenDto;
import com.uap.control_tickets.models.entity.Usuario;
import com.uap.control_tickets.services.interfaces.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Logica del login.
 *
 * Delega la verificacion de credenciales al AuthenticationManager de Spring
 * Security (que usa el UserDetailsService + BCrypt configurados). Si las
 * credenciales son correctas, genera y devuelve un JWT.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public TokenDto login(LoginDto request) {
        // 1. Autenticar: lanza BadCredentialsException si estan mal
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()));

        // 2. Usuario autenticado
        Usuario usuario = (Usuario) authentication.getPrincipal();

        // 3. Roles activos
        List<String> roles = usuario.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toList());

        // 4. Token JWT
        String token = jwtService.generarToken(usuario);

        // 5. Respuesta
        return new TokenDto(
                token,
                usuario.getIdUsuario(),
                usuario.getUsername(),
                usuario.getPersona().getNombreCompleto(),
                roles);
    }
}
