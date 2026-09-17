package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.talonario.*;
import com.uap.control_tickets.enums.TipoTalonario;
import com.uap.control_tickets.services.interfaces.TalonarioService;
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
 * Control de VENTA de boletos por talonario. Ruta base: /api/talonarios.
 *
 * No tiene relacion con el ingreso al recinto: aca solo se registra que boletos
 * de que talonario se vendieron, para poder cuadrar con cada vendedora.
 */
@RestController
@RequestMapping("/talonarios")
@RequiredArgsConstructor
@Tag(name = "Talonarios", description = "Control de venta de boletos por talonario")
public class TalonarioController {

    private final TalonarioService talonarioService;

    @GetMapping("/listar")
    @Operation(summary = "Talonarios con su avance de ventas",
            description = "soloMios=true devuelve solo los asignados al usuario logueado "
                    + "(es lo que usa la pantalla de la vendedora).")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'VENTA_FERIA')")
    public ResponseEntity<List<TalonarioDetalleDto>> listar(
            @RequestParam(required = false) TipoTalonario tipo,
            @RequestParam(defaultValue = "false") boolean soloMios) {
        return ResponseEntity.ok(talonarioService.listar(tipo, soloMios));
    }

    @GetMapping("/obtener")
    @Operation(summary = "Un talonario con su avance")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'VENTA_FERIA')")
    public ResponseEntity<TalonarioDetalleDto> obtener(@RequestParam Long idTalonario) {
        return ResponseEntity.ok(talonarioService.obtener(idTalonario));
    }

    @PostMapping("/crear")
    @Operation(summary = "Crear un talonario con su rango",
            description = "Genera los boletos del rango. Rechaza el alta si el rango se "
                    + "solapa con otro talonario DEL MISMO TIPO.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TalonarioDetalleDto> crear(@Valid @RequestBody TalonarioDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(talonarioService.crear(dto));
    }

    @PostMapping("/generar")
    @Operation(summary = "Generar varios talonarios correlativos del mismo tipo",
            description = "Ej: 20 talonarios de 200 desde el 1 -> 1-200, 201-400, ... sin huecos.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<TalonarioDetalleDto>> generar(
            @Valid @RequestBody GeneracionTalonariosDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(talonarioService.generar(dto));
    }

    @PutMapping("/actualizar")
    @Operation(summary = "Editar nombre, precio y vendedora asignada",
            description = "El tipo y el rango NO se pueden cambiar: ya generaron sus boletos.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<TalonarioDetalleDto> actualizar(
            @RequestParam Long idTalonario, @Valid @RequestBody TalonarioActualizarDto dto) {
        return ResponseEntity.ok(talonarioService.actualizar(idTalonario, dto));
    }

    @DeleteMapping("/eliminar")
    @Operation(summary = "Eliminar (lógico) un talonario. No se permite si ya tiene ventas.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@RequestParam Long idTalonario) {
        talonarioService.eliminar(idTalonario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/boletos")
    @Operation(summary = "Boletos de un talonario con su estado de venta")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'VENTA_FERIA')")
    public ResponseEntity<List<BoletoTalonarioDto>> boletos(@RequestParam Long idTalonario) {
        return ResponseEntity.ok(talonarioService.boletos(idTalonario));
    }

    @PatchMapping("/marcar")
    @Operation(summary = "Marcar boletos como vendidos, anulados o disponibles",
            description = "Tres formas: hastaNumero ('vendidos hasta el 137'), desde+hasta "
                    + "(un rango) o numeros (sueltos, uno por uno). Se pueden combinar. "
                    + "Una vendedora solo puede marcar los talonarios asignados a ella.")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'VENTA_FERIA')")
    public ResponseEntity<ResultadoMarcadoDto> marcar(@Valid @RequestBody MarcarVentaDto dto) {
        return ResponseEntity.ok(talonarioService.marcar(dto));
    }
}
