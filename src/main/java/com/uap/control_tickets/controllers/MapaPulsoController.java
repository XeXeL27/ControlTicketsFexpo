package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.mapa.MapaPulsoDetalleDto;
import com.uap.control_tickets.dto.mapa.MapaPulsoDto;
import com.uap.control_tickets.services.interfaces.MapaPulsoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Acomodo del mapa 3D del Pulso FEXPO. Ruta base: /api/mapa-pulso.
 *
 * Leer lo puede cualquiera que vea el Pulso; EDITARLO solo el ADMINISTRADOR, para
 * que nadie mueva el recinto por accidente durante la feria.
 */
@RestController
@RequestMapping("/mapa-pulso")
@RequiredArgsConstructor
@Tag(name = "Mapa del Pulso", description = "Acomodo de las zonas del mapa 3D")
public class MapaPulsoController {

    private final MapaPulsoService mapaPulsoService;

    @GetMapping
    @Operation(summary = "Acomodo guardado del mapa",
            description = "personalizado=false significa que nunca se editó: la pantalla "
                    + "usa entonces el acomodo original que viene en el código.")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_FERIA')")
    public ResponseEntity<MapaPulsoDetalleDto> obtener() {
        return ResponseEntity.ok(mapaPulsoService.obtener());
    }

    @PutMapping
    @Operation(summary = "Guardar el acomodo del mapa")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<MapaPulsoDetalleDto> guardar(@Valid @RequestBody MapaPulsoDto dto) {
        return ResponseEntity.ok(mapaPulsoService.guardar(dto));
    }

    @DeleteMapping
    @Operation(summary = "Restaurar el mapa original (borra el acomodo guardado)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> restaurar() {
        mapaPulsoService.restaurar();
        return ResponseEntity.noContent().build();
    }
}
