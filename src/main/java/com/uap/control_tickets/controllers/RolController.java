package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.rol.RolDetalleDto;
import com.uap.control_tickets.dto.rol.RolDto;
import com.uap.control_tickets.services.interfaces.RolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD de Rol. Ruta base: /api/roles. Solo ADMINISTRADOR. */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Gestion de roles del sistema")
public class RolController {

    private final RolService rolService;

    @GetMapping("/listar")
    @Operation(summary = "Listar roles activos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<RolDetalleDto>> listar() {
        return ResponseEntity.ok(rolService.listar());
    }

    @GetMapping("/obtener")
    @Operation(summary = "Obtener un rol por id")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<RolDetalleDto> obtener(@RequestParam Long idRol) {
        return ResponseEntity.ok(rolService.obtener(idRol));
    }

    @PostMapping("/crear")
    @Operation(summary = "Crear un rol")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<RolDetalleDto> crear(@Valid @RequestBody RolDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rolService.crear(dto));
    }

    @PutMapping("/actualizar")
    @Operation(summary = "Actualizar un rol")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<RolDetalleDto> actualizar(
            @RequestParam Long idRol,
            @Valid @RequestBody RolDto dto) {
        return ResponseEntity.ok(rolService.actualizar(idRol, dto));
    }

    @DeleteMapping("/eliminar")
    @Operation(summary = "Eliminar (logico) un rol")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@RequestParam Long idRol) {
        rolService.eliminar(idRol);
        return ResponseEntity.noContent().build();
    }
}
