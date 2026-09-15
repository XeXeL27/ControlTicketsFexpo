package com.uap.control_tickets.controllers.control;

import com.uap.control_tickets.dto.control.PersonaDentroDto;
import com.uap.control_tickets.dto.control.ValidacionRequestDto;
import com.uap.control_tickets.dto.control.ValidacionTicketDto;
import com.uap.control_tickets.services.interfaces.ControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Escaner de control de acceso (rol CONTROL / ADMINISTRADOR).
 *
 * Ruta base: /api/control (el prefijo /api lo agrega WebConfig).
 *  - POST /validar: escanea el qr_token, valida (SIGSE para estudiantes) y
 *    alterna ENTRADA/SALIDA. Devuelve 409 cuando el ingreso esta bloqueado.
 *  - GET /dentro: quienes estan actualmente dentro del recinto.
 */
@RestController
@RequestMapping("/control")
@RequiredArgsConstructor
@Tag(name = "Control", description = "Escaner de acceso: validacion e ingreso/egreso")
public class ControlController {

    private final ControlService controlService;

    @PostMapping("/validar")
    @Operation(summary = "Valida el qr_token escaneado y registra ENTRADA/SALIDA",
            description = "Busca el ticket por qr_token en la BD local, determina la categoria, "
                    + "consulta SIGSE si es estudiante y alterna el estado dentro/fuera. "
                    + "Si el estudiante no esta matriculado, el ingreso se bloquea (409).")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<ValidacionTicketDto> validar(@Valid @RequestBody ValidacionRequestDto request) {
        ValidacionTicketDto dto = controlService.validar(request.getCodigo());
        HttpStatus estado = dto.isBloqueado() ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(estado).body(dto);
    }

    @GetMapping("/dentro")
    @Operation(summary = "Personas que estan actualmente dentro del recinto")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<List<PersonaDentroDto>> personasDentro() {
        return ResponseEntity.ok(controlService.personasDentro());
    }
}