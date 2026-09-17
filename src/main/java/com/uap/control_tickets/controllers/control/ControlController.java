package com.uap.control_tickets.controllers.control;

import com.uap.control_tickets.apivalidacaion.Dto.ApiResponseDto;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Escaner de control de acceso (rol CONTROL / ADMINISTRADOR).
 *
 * Ruta base: /api/control (el prefijo /api lo agrega WebConfig).
 *  - POST /validar: escanea el qr_token con el escaner dedicado (ENTRADA o
 *    SALIDA). Rechaza duplicados (entrar estando dentro / salir estando fuera)
 *    y a estudiantes no matriculados con 409 + motivo en el cuerpo.
 *  - GET /dentro: quienes estan actualmente dentro del recinto.
 */
@RestController
@RequestMapping("/control")
@RequiredArgsConstructor
@Tag(name = "Control", description = "Escaner de acceso: validacion e ingreso/egreso")
public class ControlController {

    private final ControlService controlService;

    @PostMapping("/validar")
    @Operation(summary = "Valida el qr_token escaneado y registra el movimiento del escaner",
            description = "Busca el ticket por qr_token en la BD local y registra la ENTRADA o "
                    + "SALIDA segun el escaner dedicado (tipoMovimiento). Intento duplicado "
                    + "(entrar estando dentro / salir estando fuera) o estudiante no matriculado "
                    + "al entrar: 409 con motivo (YA_DENTRO/YA_FUERA/NO_MATRICULADO).")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_CONCIERTO')")
    public ResponseEntity<ValidacionTicketDto> validar(@Valid @RequestBody ValidacionRequestDto request) {
        ValidacionTicketDto dto = controlService.validar(request.getCodigo(), request.getTipoMovimiento());
        HttpStatus estado = dto.isBloqueado() ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(estado).body(dto);
    }

    @GetMapping("/dentro")
    @Operation(summary = "Personas que estan actualmente dentro del recinto")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_CONCIERTO')")
    public ResponseEntity<List<PersonaDentroDto>> personasDentro() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(controlService.personasDentro());
    }

    @GetMapping("/sigse/{ru}")
    @Operation(summary = "Consulta de matricula por RU de un estudiante",
            description = "Consulta puntual sin tocar la BD. Devuelve SIEMPRE los datos "
                    + "completos del estudiante (con o sin matricula vigente) para que el front los "
                    + "pinte de verde/rojo.")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_CONCIERTO')")
    public ResponseEntity<ApiResponseDto> consultarSigse(@PathVariable Integer ru) {
        return ResponseEntity.ok(controlService.consultarSigse(ru));
    }
}
