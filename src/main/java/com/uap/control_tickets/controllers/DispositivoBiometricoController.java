package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.huella.DispositivoBiometricoDetalleDto;
import com.uap.control_tickets.dto.huella.DispositivoBiometricoDto;
import com.uap.control_tickets.services.interfaces.DispositivoBiometricoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ABM de biométricos ZKTeco + prueba de conexión. Ruta base: /api/biometricos.
 * Solo ADMINISTRADOR.
 */
@RestController
@RequestMapping("/biometricos")
@RequiredArgsConstructor
@Tag(name = "Biométricos", description = "Equipos ZKTeco y prueba de conexión")
public class DispositivoBiometricoController {

    private final DispositivoBiometricoService biometricoService;

    @GetMapping("/listar")
    @Operation(summary = "Listar biométricos activos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<DispositivoBiometricoDetalleDto>> listar() {
        return ResponseEntity.ok(biometricoService.listar());
    }

    @GetMapping("/obtener")
    @Operation(summary = "Obtener un biométrico por id")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<DispositivoBiometricoDetalleDto> obtener(@RequestParam Long idDispositivo) {
        return ResponseEntity.ok(biometricoService.obtener(idDispositivo));
    }

    @PostMapping("/crear")
    @Operation(summary = "Registrar un biométrico (nombre + IP fija)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<DispositivoBiometricoDetalleDto> crear(@Valid @RequestBody DispositivoBiometricoDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(biometricoService.crear(dto));
    }

    @PutMapping("/actualizar")
    @Operation(summary = "Editar un biométrico")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<DispositivoBiometricoDetalleDto> actualizar(
            @RequestParam Long idDispositivo, @Valid @RequestBody DispositivoBiometricoDto dto) {
        return ResponseEntity.ok(biometricoService.actualizar(idDispositivo, dto));
    }

    @DeleteMapping("/eliminar")
    @Operation(summary = "Eliminar (lógico) un biométrico")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@RequestParam Long idDispositivo) {
        biometricoService.eliminar(idDispositivo);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/probar-conexion")
    @Operation(summary = "Conecta al equipo y devuelve sus datos (plataforma, serie, usuarios…)",
            description = "Si algo falla, el mensaje dice en qué paso fue: red, conexión rechazada, etc.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Map<String, String>> probarConexion(@RequestParam Long idDispositivo) {
        return ResponseEntity.ok(biometricoService.probarConexion(idDispositivo));
    }
}
