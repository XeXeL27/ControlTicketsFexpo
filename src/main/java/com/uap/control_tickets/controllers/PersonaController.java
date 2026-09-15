package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.persona.PersonaDetalleDto;
import com.uap.control_tickets.dto.persona.PersonaDto;
import com.uap.control_tickets.services.interfaces.PersonaService;
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
 * CRUD de Persona. Ruta base: /api/personas
 * Todo el modulo requiere rol ADMINISTRADOR (@PreAuthorize a nivel de metodo).
 */
@RestController
@RequestMapping("/personas")
@RequiredArgsConstructor
@Tag(name = "Personas", description = "Gestion de datos personales")
public class PersonaController {

    private final PersonaService personaService;

    @GetMapping("/listar")
    @Operation(summary = "Listar personas activas")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<PersonaDetalleDto>> listar() {
        return ResponseEntity.ok(personaService.listar());
    }

    @GetMapping("/obtener")
    @Operation(summary = "Obtener una persona por id")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PersonaDetalleDto> obtener(@RequestParam Long idPersona) {
        return ResponseEntity.ok(personaService.obtener(idPersona));
    }

    @PostMapping("/crear")
    @Operation(summary = "Crear una persona")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PersonaDetalleDto> crear(@Valid @RequestBody PersonaDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(personaService.crear(dto));
    }

    @PutMapping("/actualizar")
    @Operation(summary = "Actualizar una persona")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PersonaDetalleDto> actualizar(
            @RequestParam Long idPersona,
            @Valid @RequestBody PersonaDto dto) {
        return ResponseEntity.ok(personaService.actualizar(idPersona, dto));
    }

    @DeleteMapping("/eliminar")
    @Operation(summary = "Eliminar (logico) una persona")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@RequestParam Long idPersona) {
        personaService.eliminar(idPersona);
        return ResponseEntity.noContent().build();
    }
}
