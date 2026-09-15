package com.uap.control_tickets.controllers.auth;

import com.uap.control_tickets.dto.login.LoginDto;
import com.uap.control_tickets.dto.login.TokenDto;
import com.uap.control_tickets.services.interfaces.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint publico de acceso. Ruta final: POST /api/auth/login
 * (el prefijo /api lo agrega WebConfig).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticacion", description = "Endpoints de acceso al sistema")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion", description = "Autentica al usuario y devuelve un JWT")
    public ResponseEntity<TokenDto> login(@Valid @RequestBody LoginDto request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
