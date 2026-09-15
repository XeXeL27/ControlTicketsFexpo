package com.uap.control_tickets.config.security;

import com.uap.control_tickets.models.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fabrica y valida los tokens JWT.
 *
 * Un JWT es un texto firmado que contiene datos del usuario (username, id, roles)
 * y una fecha de expiracion. Al ir firmado con una clave secreta, el servidor
 * puede confiar en el sin guardar sesiones (autenticacion "stateless").
 *
 * La clave (app.jwt.secret) y la duracion (app.jwt.expiration-ms) vienen del
 * archivo de propiedades, nunca escritas en el codigo.
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /** Genera un token firmado para el usuario que acaba de loguearse. */
    public String generarToken(Usuario usuario) {
        return Jwts.builder()
                .subject(usuario.getUsername())
                .claim("idUsuario", usuario.getIdUsuario())
                .claim("nombreCompleto", usuario.getPersona().getNombreCompleto())
                .claim("roles", getRoles(usuario))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSecretKey())
                .compact();
    }

    /** Devuelve true si el token es autentico y no expiro. */
    public boolean validarToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extraerUsername(String token) {
        return getClaims(token).getSubject();
    }

    public Long extraerIdUsuario(String token) {
        return getClaims(token).get("idUsuario", Long.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> extraerRoles(String token) {
        return (List<String>) getClaims(token).get("roles");
    }

    // ---------------- helpers privados ----------------

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> getRoles(Usuario usuario) {
        return usuario.getAuthorities().stream()
                .map(GrantedAuthorityName -> GrantedAuthorityName.getAuthority())
                .collect(Collectors.toList());
    }
}
