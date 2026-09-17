package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.huella.HuellaDigitalDto;
import com.uap.control_tickets.dto.huella.ProgresoHuellaDto;
import com.uap.control_tickets.dto.huella.ResultadoHuellaDto;
import com.uap.control_tickets.services.interfaces.SincronizacionHuellaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sincronización de huellas con los biométricos. Ruta base: /api/huellas.
 * Solo ADMINISTRADOR.
 *
 * Flujo de la pantalla: POST /sincronizar → devuelve el jobId → la pantalla
 * muestra la barra con WS (/topic/huellas/{jobId}) + polling a /progreso →
 * al terminar, GET /resultado para el detalle por RU.
 */
@RestController
@RequestMapping("/huellas")
@RequiredArgsConstructor
@Tag(name = "Huellas", description = "Sincronización de huellas con biométricos ZKTeco")
public class SincronizacionHuellaController {

    private final SincronizacionHuellaService sincronizacionService;

    @PostMapping("/sincronizar")
    @Operation(summary = "Inicia la sincronización (devuelve el jobId enseguida)",
            description = "Body opcional: lista de ids de equipos. Vacío = todos los activos.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Map<String, Long>> sincronizar(@RequestBody(required = false) List<Long> idsEquipos) {
        Long jobId = sincronizacionService.iniciar(idsEquipos);
        return ResponseEntity.ok(Map.of("jobId", jobId));
    }

    @GetMapping("/progreso")
    @Operation(summary = "Progreso actual del job (para polling de respaldo al WS)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ProgresoHuellaDto> progreso(@RequestParam Long jobId) {
        return ResponseEntity.ok(sincronizacionService.progreso(jobId));
    }

    @GetMapping("/resultado")
    @Operation(summary = "Reporte final por RU",
            description = "filtroEstado: TODOS (defecto), CORRECTO, DUPLICADO, NO_ENCONTRADO, SIN_HUELLA, ERROR.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ResultadoHuellaDto> resultado(
            @RequestParam Long jobId,
            @RequestParam(required = false, defaultValue = "TODOS") String filtroEstado) {
        return ResponseEntity.ok(sincronizacionService.resultado(jobId, filtroEstado));
    }

    @GetMapping("/historial")
    @Operation(summary = "Historial de sincronizaciones (recientes primero)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<ProgresoHuellaDto>> historial() {
        return ResponseEntity.ok(sincronizacionService.historial());
    }

    @PostMapping("/cancelar")
    @Operation(summary = "Pide cancelar una sincronización en curso")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> cancelar(@RequestParam Long jobId) {
        sincronizacionService.cancelar(jobId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/estudiante")
    @Operation(summary = "Las N huellas guardadas de un estudiante (dedo, equipo, fecha)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<HuellaDigitalDto>> huellasDeEstudiante(@RequestParam Long idEstudiante) {
        return ResponseEntity.ok(sincronizacionService.huellasDeEstudiante(idEstudiante));
    }
}
