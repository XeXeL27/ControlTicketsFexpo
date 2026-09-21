package com.uap.control_tickets.controllers.control;

import com.uap.control_tickets.dto.control.RegularizacionAccesoDto;
import com.uap.control_tickets.dto.control.ResultadoRegularizacionAccesoDto;
import com.uap.control_tickets.dto.control.ValidacionTalonarioDto;
import com.uap.control_tickets.dto.control.ValidacionTalonarioRequestDto;
import com.uap.control_tickets.services.interfaces.ControlTalonarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puesto del concierto para boletos vendidos por talonario (rol
 * CONTROL_CONCIERTO / ADMINISTRADOR): el particular trae un papel numerado sin
 * QR, así que se tipea el número y el puesto sabe en qué evento está.
 *
 * Ruta base: /api/control/talonarios (el prefijo /api lo agrega WebConfig).
 */
@RestController
@RequestMapping("/control/talonarios")
@RequiredArgsConstructor
@Tag(name = "Control de talonarios", description = "Puesto del concierto: ingreso/egreso por número")
public class ControlTalonarioController {

    private final ControlTalonarioService controlTalonarioService;

    @PostMapping("/validar")
    @Operation(summary = "Valida el número tipeado y registra el movimiento",
            description = "Busca el boleto N dentro del par (CONCIERTO, evento) y registra la "
                    + "ENTRADA o SALIDA según el escáner dedicado (tipoMovimiento). El ANULADO se "
                    + "rechaza; el DISPONIBLE pasa (la venta se regulariza después). Rechazos con "
                    + "409 + motivo: ANULADO, DIA_INCORRECTO, "
                    + "FUERA_DE_FECHA, YA_DENTRO, YA_FUERA. Número inexistente: 404.")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_CONCIERTO')")
    public ResponseEntity<ValidacionTalonarioDto> validar(
            @Valid @RequestBody ValidacionTalonarioRequestDto request) {
        ValidacionTalonarioDto dto = controlTalonarioService.validar(
                request.getNumero(), request.getTipoEvento(), request.getTipoMovimiento());
        HttpStatus estado = dto.isBloqueado() ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(estado).body(dto);
    }

    @PostMapping("/regularizar-ingreso")
    @Operation(summary = "Regularizar un ingreso por número (solo administrador)",
            description = "Registra una ENTRADA con la fecha del día pedido: para ingresos "
                    + "que pasaron por puerta sin escaneo. Si el boleto ya tiene una "
                    + "ENTRADA ese día, se rechaza con 400 (\"ya tenía registro\"). El "
                    + "\"dentro\" solo se toca si el día pedido es hoy.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ResultadoRegularizacionAccesoDto> regularizarIngreso(
            @Valid @RequestBody RegularizacionAccesoDto request) {
        return ResponseEntity.ok(controlTalonarioService.regularizarIngreso(
                request.getNumero(), request.getTipoEvento(), request.getDia()));
    }
}
