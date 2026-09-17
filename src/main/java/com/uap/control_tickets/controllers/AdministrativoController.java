package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.administrativo.AdministrativoDetalleDto;
import com.uap.control_tickets.dto.administrativo.AdministrativoDto;
import com.uap.control_tickets.dto.administrativo.PrevisualizacionAdmCsvDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.services.interfaces.AdministrativoService;
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

/** CRUD de Administrativo + carga masiva por CSV. Ruta base: /api/administrativos. */
@RestController
@RequestMapping("/administrativos")
@RequiredArgsConstructor
@Tag(name = "Administrativos", description = "Gestión de administrativos y carga masiva")
public class AdministrativoController {

    private final com.uap.control_tickets.services.impl.CambioTipoService cambioTipoService;

    private final AdministrativoService administrativoService;

    @PostMapping("/cambiar-a-docente")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> cambiarTipo(@RequestParam Long idAdministrativo,
            @Valid @RequestBody com.uap.control_tickets.dto.persona.CambioTipoDto dto) {
        cambioTipoService.aDocente(idAdministrativo, dto.getCarrera());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/listar")
    @Operation(summary = "Listar administrativos activos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<AdministrativoDetalleDto>> listar() {
        return ResponseEntity.ok(administrativoService.listar());
    }

    @GetMapping("/obtener")
    @Operation(summary = "Obtener un administrativo por id")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AdministrativoDetalleDto> obtener(@RequestParam Long idAdministrativo) {
        return ResponseEntity.ok(administrativoService.obtener(idAdministrativo));
    }

    @PostMapping("/crear")
    @Operation(summary = "Crear un administrativo")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AdministrativoDetalleDto> crear(@Valid @RequestBody AdministrativoDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(administrativoService.crear(dto));
    }

    @PostMapping(value = "/importar", consumes = "multipart/form-data")
    @Operation(summary = "Importar administrativos desde un CSV",
            description = "Columnas: codigo administrativo, nombre completo, ci")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ImportacionResultadoDto> importar(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(administrativoService.importarCsv(archivo));
    }

    @PostMapping(value = "/previsualizar", consumes = "multipart/form-data")
    @Operation(summary = "Previsualizar un CSV de administrativos sin guardar nada",
            description = "Corre el mismo parser que la importación real y devuelve codificación, "
                    + "separador, encabezado y las primeras filas ya parseadas.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PrevisualizacionAdmCsvDto> previsualizar(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(administrativoService.previsualizarCsv(archivo));
    }

    @DeleteMapping("/eliminar")
    @Operation(summary = "Eliminar (lógico) un administrativo")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@RequestParam Long idAdministrativo) {
        administrativoService.eliminar(idAdministrativo);
        return ResponseEntity.noContent().build();
    }
}
