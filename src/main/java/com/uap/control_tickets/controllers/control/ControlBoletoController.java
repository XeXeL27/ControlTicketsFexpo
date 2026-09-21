package com.uap.control_tickets.controllers.control;

import com.uap.control_tickets.dto.control.BoletoDentroDto;
import com.uap.control_tickets.dto.control.RegularizacionAccesoDto;
import com.uap.control_tickets.dto.control.ReporteIngresosFeriaDto;
import com.uap.control_tickets.dto.control.ResumenBoletosDto;
import com.uap.control_tickets.dto.control.ResultadoRegularizacionAccesoDto;
import com.uap.control_tickets.dto.control.RegistroSalidaDetalleDto;
import com.uap.control_tickets.dto.control.RegistroSalidaDto;
import com.uap.control_tickets.dto.control.ValidacionBoletoDto;
import com.uap.control_tickets.dto.control.ValidacionBoletoRequestDto;
import com.uap.control_tickets.services.interfaces.ControlBoletoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Escaner de control de ingreso a la feria (rol CONTROL / ADMINISTRADOR), para
 * los boletos de venta (anonimos, cargados por CSV). Mismo patron que
 * {@link ControlController} (tickets de estudiante), sin matricula.
 *
 * Ruta base: /api/control/boletos (el prefijo /api lo agrega WebConfig).
 * El tiempo real (para la tabla de Control y el monitoreo Pulso FEXPO) va por
 * WebSocket (STOMP), tema /topic/boletos — ver {@link com.uap.control_tickets.config.WebSocketConfig}
 * y {@link com.uap.control_tickets.services.impl.BoletoEventosPublisher}, no por un endpoint REST.
 */
@RestController
@RequestMapping("/control/boletos")
@RequiredArgsConstructor
@Tag(name = "Control de boletos", description = "Validacion de ingreso/egreso de boletos de la feria")
public class ControlBoletoController {

    private final ControlBoletoService controlBoletoService;

    @PostMapping("/validar")
    @Operation(summary = "Valida el codigo escaneado/tipeado y registra el movimiento",
            description = "Busca el boleto por (tipo, codigo) y registra la ENTRADA o SALIDA segun el "
                    + "escaner dedicado (tipoMovimiento). Intento duplicado (entrar estando dentro / "
                    + "salir estando fuera): 409 con motivo (YA_DENTRO/YA_FUERA). Codigo inexistente "
                    + "en ese tipo: 404.")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_FERIA')")
    public ResponseEntity<ValidacionBoletoDto> validar(@Valid @RequestBody ValidacionBoletoRequestDto request) {
        ValidacionBoletoDto dto = controlBoletoService.validar(
                request.getCodigo(), request.getTipoMovimiento(), request.getTipoBoleto());
        HttpStatus estado = dto.isBloqueado() ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(estado).body(dto);
    }

    @GetMapping("/dentro")
    @Operation(summary = "Boletos que estan actualmente dentro del recinto")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_FERIA')")
    public ResponseEntity<List<BoletoDentroDto>> boletosDentro() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(controlBoletoService.boletosDentro());
    }

    @GetMapping("/resumen")
    @Operation(summary = "Foto del estado actual (dentro, total, ingresos y salidas)")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_FERIA')")
    public ResponseEntity<ResumenBoletosDto> resumen() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(controlBoletoService.resumen());
    }

    @GetMapping("/reporte-ingresos")
    @Operation(summary = "Ingresos (ENTRADAS) de los 3 días de la feria",
            description = "Un elemento por día (DIA_1/2/3) con ENTRADAS separadas en "
                    + "FERIA y PARQUEO, más los totales del evento. Base del apartado "
                    + "\"Reportes\" y de su exportación a PDF. Solo cuenta ENTRADAS; "
                    + "salidas e intentos denegados no cuentan.")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_FERIA')")
    public ResponseEntity<ReporteIngresosFeriaDto> reporteIngresos() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(controlBoletoService.reporteIngresosPorDia());
    }


    @GetMapping(value = "/registro-salida/foto", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(summary = "Foto del visitante registrada al salir",
            description = "Devuelve el JPEG guardado en la carpeta de fotos. 404 si ese "
                    + "registro no tiene foto. Exige token, así que el frontend la pide "
                    + "con responseType 'blob' y no con <img src>.")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_FERIA')")
    public ResponseEntity<byte[]> fotoDeRegistro(@RequestParam Long idRegistro) {
        byte[] jpg = controlBoletoService.fotoDeRegistro(idRegistro);
        return jpg == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(jpg);
    }

    @PostMapping("/registro-salida")
    @Operation(summary = "Registrar los datos del visitante que dijo que va a volver",
            description = "Se llama DESPUÉS de la salida, nunca antes: la salida no puede "
                    + "depender de este formulario. Nombre, CI y foto son opcionales; "
                    + "sin ninguno queda sinDatos=true como respaldo de que se preguntó.")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_FERIA')")
    public ResponseEntity<RegistroSalidaDetalleDto> registrarSalida(
            @Valid @RequestBody RegistroSalidaDto dto) {
        return ResponseEntity.ok(controlBoletoService.registrarSalida(dto));
    }

    @PostMapping("/cierre-jornada")
    @Operation(summary = "Cierra la jornada: deja a todos (tickets y boletos) como fuera",
            description = "Se usa al terminar cada dia de la feria. Evita que un 'dentro' "
                    + "que quedo colgado ensucie el monitoreo del dia siguiente.")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL_FERIA')")
    public ResponseEntity<Integer> cerrarJornada() {
        return ResponseEntity.ok(controlBoletoService.cerrarJornada());
    }

    @PostMapping("/regularizar-ingreso")
    @Operation(summary = "Regularizar un ingreso de feria/parqueo (solo administrador)",
            description = "Registra una ENTRADA con la fecha del día pedido: para ingresos "
                    + "que pasaron por puerta sin escaneo. Si el boleto ya tiene una "
                    + "ENTRADA ese día, se rechaza con 400 (\"ya tenía registro\"). El "
                    + "\"dentro\" solo se toca si el día pedido es hoy.")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<ResultadoRegularizacionAccesoDto> regularizarIngreso(
            @Valid @RequestBody RegularizacionAccesoDto request) {
        return ResponseEntity.ok(controlBoletoService.regularizarIngreso(
                request.getCodigo(), request.getTipoBoleto(), request.getDia()));
    }
}
