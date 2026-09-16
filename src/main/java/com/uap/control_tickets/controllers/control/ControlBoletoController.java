package com.uap.control_tickets.controllers.control;

import com.uap.control_tickets.dto.control.BoletoDentroDto;
import com.uap.control_tickets.dto.control.ResumenBoletosDto;
import com.uap.control_tickets.dto.control.ValidacionBoletoDto;
import com.uap.control_tickets.dto.control.ValidacionBoletoRequestDto;
import com.uap.control_tickets.services.impl.BoletoEventosPublisher;
import com.uap.control_tickets.services.interfaces.ControlBoletoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Escaner de control de ingreso a la feria (rol CONTROL / ADMINISTRADOR), para
 * los boletos de venta (anonimos, cargados por CSV). Mismo patron que
 * {@link ControlController} (tickets de estudiante), sin matricula.
 *
 * Ruta base: /api/control/boletos (el prefijo /api lo agrega WebConfig).
 */
@RestController
@RequestMapping("/control/boletos")
@RequiredArgsConstructor
@Tag(name = "Control de boletos", description = "Validacion de ingreso/egreso de boletos de la feria + monitoreo en vivo")
public class ControlBoletoController {

    private final ControlBoletoService controlBoletoService;
    private final BoletoEventosPublisher eventos;

    @PostMapping("/validar")
    @Operation(summary = "Valida el codigo escaneado/tipeado y registra el movimiento",
            description = "Busca el boleto por codigo y registra la ENTRADA o SALIDA segun el "
                    + "escaner dedicado (tipoMovimiento). Intento duplicado (entrar estando dentro / "
                    + "salir estando fuera): 409 con motivo (YA_DENTRO/YA_FUERA). Codigo inexistente: 404.")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<ValidacionBoletoDto> validar(@Valid @RequestBody ValidacionBoletoRequestDto request) {
        ValidacionBoletoDto dto = controlBoletoService.validar(request.getCodigo(), request.getTipoMovimiento());
        HttpStatus estado = dto.isBloqueado() ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(estado).body(dto);
    }

    @GetMapping("/dentro")
    @Operation(summary = "Boletos que estan actualmente dentro del recinto")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<List<BoletoDentroDto>> boletosDentro() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(controlBoletoService.boletosDentro());
    }

    @GetMapping("/resumen")
    @Operation(summary = "Foto del estado actual (dentro, total, ingresos y salidas)")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public ResponseEntity<ResumenBoletosDto> resumen() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(controlBoletoService.resumen());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Conexion en vivo (SSE): un evento por cada validacion de boleto",
            description = "Deja la conexion abierta y empuja un evento 'boleto' cada vez que alguien "
                    + "valida un codigo (ENTRADA/SALIDA/BLOQUEADO/NO_VALIDO), con el contador dentroAhora "
                    + "ya actualizado. El cliente debe mandar el header Authorization (EventSource nativo "
                    + "no permite headers, por eso el frontend usa fetch + ReadableStream).")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
    public SseEmitter stream() {
        return eventos.suscribir();
    }
}
