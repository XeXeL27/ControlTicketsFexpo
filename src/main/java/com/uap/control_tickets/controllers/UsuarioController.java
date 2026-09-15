package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.usuario.CambioPasswordDto;
import com.uap.control_tickets.dto.usuario.UsuarioDetalleDto;
import com.uap.control_tickets.dto.usuario.UsuarioDto;
import com.uap.control_tickets.dto.usuario.UsuarioRolDto;
import com.uap.control_tickets.services.interfaces.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD de Usuario + gestion de roles/credenciales. Ruta base: /api/usuarios.
 *
 * La mayoria de acciones son solo para ADMINISTRADOR. La excepcion es
 * "mi-perfil": cualquier usuario autenticado puede ver SU propia cuenta.
 */
@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Gestion de cuentas de acceso")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/listar")
    @Operation(summary = "Listar usuarios activos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<UsuarioDetalleDto>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    @GetMapping("/mi-perfil")
    @Operation(summary = "Ver el perfil del usuario logueado")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UsuarioDetalleDto> miPerfil() {
        return ResponseEntity.ok(usuarioService.miPerfil());
    }

    @GetMapping("/obtener")
    @Operation(summary = "Obtener un usuario por id")
    @PreAuthorize("hasRole('ADMINISTRADOR') or @autorizacionService.esUsuarioActual(#idUsuario)")
    public ResponseEntity<UsuarioDetalleDto> obtener(@RequestParam Long idUsuario) {
        return ResponseEntity.ok(usuarioService.obtener(idUsuario));
    }

    @PostMapping("/crear")
    @Operation(summary = "Crear un usuario")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<UsuarioDetalleDto> crear(@Valid @RequestBody UsuarioDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(dto));
    }

    @PutMapping("/actualizar")
    @Operation(summary = "Actualizar un usuario")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<UsuarioDetalleDto> actualizar(
            @RequestParam Long idUsuario,
            @Valid @RequestBody UsuarioDto dto) {
        return ResponseEntity.ok(usuarioService.actualizar(idUsuario, dto));
    }

    @DeleteMapping("/eliminar")
    @Operation(summary = "Eliminar (logico) un usuario")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@RequestParam Long idUsuario) {
        usuarioService.eliminar(idUsuario);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/bloqueo")
    @Operation(summary = "Bloquear o desbloquear un usuario")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<UsuarioDetalleDto> cambiarBloqueo(
            @RequestParam Long idUsuario,
            @RequestParam boolean bloqueado) {
        return ResponseEntity.ok(usuarioService.cambiarBloqueo(idUsuario, bloqueado));
    }

    @PatchMapping("/password")
    @Operation(summary = "Restablecer la contrasena de un usuario")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> cambiarPassword(
            @RequestParam Long idUsuario,
            @Valid @RequestBody CambioPasswordDto dto) {
        usuarioService.cambiarPassword(idUsuario, dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/asignar-rol")
    @Operation(summary = "Asignar un rol a un usuario")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<UsuarioDetalleDto> asignarRol(@Valid @RequestBody UsuarioRolDto dto) {
        return ResponseEntity.ok(usuarioService.asignarRol(dto));
    }

    @PostMapping("/quitar-rol")
    @Operation(summary = "Quitar un rol a un usuario")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<UsuarioDetalleDto> quitarRol(@Valid @RequestBody UsuarioRolDto dto) {
        return ResponseEntity.ok(usuarioService.quitarRol(dto));
    }
}
