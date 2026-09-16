package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.boleto.BoletoDetalleDto;
import com.uap.control_tickets.dto.boleto.BoletoDto;
import com.uap.control_tickets.dto.boleto.PrevisualizacionBoletoCsvDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.services.interfaces.BoletoService;
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
 * CRUD de Boleto (venta de entrada a la feria) + carga masiva por CSV.
 * Ruta base: /api/boletos. La validación de ingreso/salida está en
 * {@link com.uap.control_tickets.controllers.control.ControlBoletoController}.
 */
@RestController
@RequestMapping("/boletos")
@RequiredArgsConstructor
@Tag(name = "Boletos", description = "Boletos de venta para la feria: alta y carga masiva")
public class BoletoController {

    private final BoletoService boletoService;

    @GetMapping("/listar")
    @Operation(summary = "Listar boletos activos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<List<BoletoDetalleDto>> listar() {
        return ResponseEntity.ok(boletoService.listar());
    }

    @GetMapping("/obtener")
    @Operation(summary = "Obtener un boleto por id")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<BoletoDetalleDto> obtener(@RequestParam Long idBoleto) {
        return ResponseEntity.ok(boletoService.obtener(idBoleto));
    }

    @PostMapping("/crear")
    @Operation(summary = "Dar de alta un boleto (código suelto)")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<BoletoDetalleDto> crear(@Valid @RequestBody BoletoDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boletoService.crear(dto));
    }

    @PostMapping(value = "/importar", consumes = "multipart/form-data")
    @Operation(summary = "Importar boletos desde un CSV",
            description = "Una columna: el código del boleto. Reimportar el mismo listado no falla "
                    + "(los códigos repetidos se saltean sin tocar su estado dentro/fuera).")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ImportacionResultadoDto> importar(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(boletoService.importarCsv(archivo));
    }

    @PostMapping(value = "/previsualizar", consumes = "multipart/form-data")
    @Operation(summary = "Previsualizar un CSV de boletos sin guardar nada")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PrevisualizacionBoletoCsvDto> previsualizar(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(boletoService.previsualizarCsv(archivo));
    }

    @DeleteMapping("/eliminar")
    @Operation(summary = "Eliminar (lógico) un boleto")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@RequestParam Long idBoleto) {
        boletoService.eliminar(idBoleto);
        return ResponseEntity.noContent().build();
    }
}
