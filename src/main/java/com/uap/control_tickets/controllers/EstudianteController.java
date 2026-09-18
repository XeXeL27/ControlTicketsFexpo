package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.estudiante.EstudianteDetalleDto;
import com.uap.control_tickets.dto.estudiante.EstudianteDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.dto.estudiante.PrevisualizacionCsvDto;
import com.uap.control_tickets.services.interfaces.EstudianteService;
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

/**
 * CRUD de Estudiante + carga masiva por CSV. Ruta base: /api/estudiantes.
 * Solo ADMINISTRADOR.
 */
@RestController
@RequestMapping("/estudiantes")
@RequiredArgsConstructor
@Tag(name = "Estudiantes", description = "Gestión de estudiantes y carga masiva")
public class EstudianteController {

    private final EstudianteService estudianteService;

    @GetMapping("/listar")
    @Operation(summary = "Listar estudiantes activos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<EstudianteDetalleDto>> listar() {
        return ResponseEntity.ok(estudianteService.listar());
    }

    @GetMapping("/obtener")
    @Operation(summary = "Obtener un estudiante por id")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstudianteDetalleDto> obtener(@RequestParam Long idEstudiante) {
        return ResponseEntity.ok(estudianteService.obtener(idEstudiante));
    }

    @PostMapping("/crear")
    @Operation(summary = "Crear un estudiante")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstudianteDetalleDto> crear(@Valid @RequestBody EstudianteDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(estudianteService.crear(dto));
    }

    @PostMapping(value = "/importar", consumes = "multipart/form-data")
    @Operation(summary = "Importar estudiantes desde un CSV",
            description = "Columnas: nombre,paterno,materno,ci,ru,facultad,carrera")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ImportacionResultadoDto> importar(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(estudianteService.importarCsv(archivo));
    }

    @PostMapping(value = "/previsualizar", consumes = "multipart/form-data")
    @Operation(summary = "Lee el CSV y muestra que pasaria al importarlo, sin guardar nada")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PrevisualizacionCsvDto> previsualizar(
            @RequestPart("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(estudianteService.previsualizarCsv(archivo));
    }

    @DeleteMapping("/eliminar")
    @Operation(summary = "Eliminar (lógico) un estudiante")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@RequestParam Long idEstudiante) {
        estudianteService.eliminar(idEstudiante);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/facultades")
    @Operation(summary = "Facultades distintas (para la carga masiva al biométrico)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<String>> facultades() {
        return ResponseEntity.ok(estudianteService.facultades());
    }

    @GetMapping("/carreras")
    @Operation(summary = "Carreras distintas (para la carga masiva al biométrico)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<String>> carreras() {
        return ResponseEntity.ok(estudianteService.carreras());
    }
}
