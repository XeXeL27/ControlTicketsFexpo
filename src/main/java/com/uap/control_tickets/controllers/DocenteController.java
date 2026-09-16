package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.docente.DocenteDetalleDto;
import com.uap.control_tickets.dto.docente.DocenteDto;
import com.uap.control_tickets.dto.docente.PrevisualizacionDocCsvDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.services.interfaces.DocenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** CRUD de Docente + carga masiva por CSV. Ruta base: /api/docentes. */
@RestController
@RequestMapping("/docentes")
@RequiredArgsConstructor
@Tag(name = "Docentes", description = "Gestión de docentes y carga masiva")
public class DocenteController {

    private final DocenteService docenteService;

    @GetMapping("/listar")
    @Operation(summary = "Listar docentes activos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<DocenteDetalleDto>> listar() {
        return ResponseEntity.ok(docenteService.listar());
    }

    @GetMapping("/obtener")
    @Operation(summary = "Obtener un docente por id")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<DocenteDetalleDto> obtener(@RequestParam Long idDocente) {
        return ResponseEntity.ok(docenteService.obtener(idDocente));
    }

    @PostMapping("/crear")
    @Operation(summary = "Crear un docente")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<DocenteDetalleDto> crear(@Valid @RequestBody DocenteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(docenteService.crear(dto));
    }

    @PostMapping(value = "/importar", consumes = "multipart/form-data")
    @Operation(summary = "Importar docentes desde un CSV",
            description = "Columnas: codigo docente, nombre completo, ci")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ImportacionResultadoDto> importar(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(docenteService.importarCsv(archivo));
    }

    @PostMapping(value = "/previsualizar", consumes = "multipart/form-data")
    @Operation(summary = "Previsualizar un CSV de docentes sin guardar nada")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PrevisualizacionDocCsvDto> previsualizar(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(docenteService.previsualizarCsv(archivo));
    }

    @DeleteMapping("/eliminar")
    @Operation(summary = "Eliminar (lógico) un docente")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@RequestParam Long idDocente) {
        docenteService.eliminar(idDocente);
        return ResponseEntity.noContent().build();
    }
}
