package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.control.ResultadoRegularizacionAccesoDto;
import com.uap.control_tickets.dto.control.ValidacionTalonarioDto;
import com.uap.control_tickets.enums.DestinoTalonario;
import com.uap.control_tickets.enums.DiaFeria;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.EstadoVenta;
import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.enums.TipoTalonario;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.BoletoTalonario;
import com.uap.control_tickets.models.entity.MovimientoTalonario;
import com.uap.control_tickets.models.repository.BoletoTalonarioDao;
import com.uap.control_tickets.models.repository.MovimientoTalonarioDao;
import com.uap.control_tickets.services.interfaces.ControlTalonarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Puesto del concierto por número de talonario.
 *
 * Flujo por validación (calcado del validador de boletos de feria):
 *  1. Busca el boleto N dentro del par (CONCIERTO, evento). 404 si no existe.
 *  2. ANULADO → 409 (no vale nunca). El DISPONIBLE sí pasa: la venta a veces
 *     se regulariza después que la gente llega a la puerta.
 *  3. Día del evento (solo ENTRADA): EVENTO_1/2/3 solo su día, COMBO cualquiera.
 *  4. Anti-clones por flag dentro (con limpieza del 'dentro' colgado de otro día).
 *  5. Registra el movimiento y actualiza el flag.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlTalonarioServiceImpl implements ControlTalonarioService {

    /** Este puesto es la puerta del concierto: el destino no se elige. */
    private static final DestinoTalonario DESTINO = DestinoTalonario.CONCIERTO;

    private final BoletoTalonarioDao boletoTalonarioDao;
    private final MovimientoTalonarioDao movimientoTalonarioDao;
    private final CalendarioFeria calendario;

    @Override
    @Transactional
    public ValidacionTalonarioDto validar(Integer numero, TipoTalonario tipoEvento, TipoAcceso tipoMovimiento) {
        if (numero == null || numero <= 0) {
            throw new NegocioException("Ingrese el número del boleto");
        }
        if (tipoEvento == null) {
            throw new NegocioException("Indique el evento (EVENTO_1, EVENTO_2, EVENTO_3 o COMBO)");
        }
        if (tipoMovimiento == null) {
            throw new NegocioException("Indique el tipo de movimiento (ENTRADA o SALIDA)");
        }

        List<BoletoTalonario> candidatos = boletoTalonarioDao.buscarParaPuerta(
                DESTINO, tipoEvento, numero, EstadoRegistro.ACTIVO);
        if (candidatos.isEmpty()) {
            throw new RecursoNoEncontradoException("No existe el boleto " + numero
                    + " para " + tipoEvento.etiqueta() + " (" + DESTINO.etiqueta() + ")");
        }
        if (candidatos.size() > 1) {
            log.warn("El número {} de {}/{} está en {} talonarios (los rangos se solapan); "
                    + "la puerta toma el primero.",
                    numero, DESTINO, tipoEvento, candidatos.size());
        }
        BoletoTalonario boleto = candidatos.get(0);

        ValidacionTalonarioDto dto = new ValidacionTalonarioDto();
        dto.setIdBoletoTalonario(boleto.getIdBoletoTalonario());
        dto.setNumero(boleto.getNumero());
        dto.setNombreTalonario(boleto.getTalonario().getNombre());
        dto.setTipoEvento(tipoEvento.name());
        dto.setDentro(boleto.isDentro());

        // 'dentro' colgado de un día anterior: se fue sin escanear la salida.
        // Vale también para el COMBO (entra las tres noches, cada día arranca fuera).
        if (boleto.isDentro() && esDentroVencido(boleto)) {
            boleto.setDentro(false);
            dto.setDentro(false);
        }

        // ANULADO no vale nunca. El DISPONIBLE (todavía no marcado como vendido)
        // SÍ pasa: la rendición de la vendedora a veces llega después que la
        // gente a la puerta, y frenarla ahí sería peor que dejarla entrar.
        if (boleto.getEstadoVenta() == EstadoVenta.ANULADO) {
            return bloqueado(dto, "ANULADO",
                    "El boleto " + numero + " está ANULADO y no permite el ingreso.");
        }

        // Día del evento (solo al ENTRAR): el evento puntual solo sirve su día, el
        // COMBO habilita las tres noches y no se valida.
        if (tipoMovimiento == TipoAcceso.ENTRADA && calendario.validacionActiva()
                && tipoEvento != TipoTalonario.COMBO) {
            DiaFeria dia = diaDe(tipoEvento);
            var hoy = calendario.diaDeHoy();
            if (hoy == null) {
                return bloqueado(dto, "FUERA_DE_FECHA",
                        "Hoy no es un día del evento. Este boleto es para "
                                + calendario.describir(dia) + ".");
            }
            if (hoy != dia) {
                return bloqueado(dto, "DIA_INCORRECTO",
                        "Este boleto es para " + calendario.describir(dia)
                                + " y hoy es " + calendario.describir(hoy) + ".");
            }
        }

        // Anti-clones: el escáner es dedicado, el estado tiene que coincidir.
        if (tipoMovimiento == TipoAcceso.ENTRADA && boleto.isDentro()) {
            return bloqueado(dto, "YA_DENTRO",
                    "El boleto " + numero + " ya está DENTRO (ENTRADA ya registrada).");
        }
        if (tipoMovimiento == TipoAcceso.SALIDA && !boleto.isDentro()) {
            return bloqueado(dto, "YA_FUERA",
                    "El boleto " + numero + " está FUERA (no hay ENTRADA registrada).");
        }

        // Registrar el movimiento del escáner dedicado.
        boleto.setDentro(tipoMovimiento == TipoAcceso.ENTRADA);

        MovimientoTalonario mov = new MovimientoTalonario();
        mov.setBoletoTalonario(boleto);
        mov.setTipo(tipoMovimiento);
        mov.setFechaHora(Instant.now());
        movimientoTalonarioDao.save(mov);
        boletoTalonarioDao.save(boleto);

        dto.setDentro(boleto.isDentro());
        dto.setUltimoTipo(tipoMovimiento.name());
        dto.setUltimaFecha(mov.getFechaHora());
        if (boleto.isDentro()) dto.setEntrada(mov.getFechaHora());
        return dto;
    }

    @Override
    @Transactional
    public ResultadoRegularizacionAccesoDto regularizarIngreso(
            Integer numero, TipoTalonario tipoEvento, DiaFeria dia) {
        if (numero == null || numero <= 0) {
            throw new NegocioException("Ingrese el número del boleto");
        }
        if (tipoEvento == null) {
            throw new NegocioException("Indique el evento (EVENTO_1, EVENTO_2, EVENTO_3 o COMBO)");
        }
        if (dia == null) {
            throw new NegocioException("Indique el día del ingreso");
        }
        List<BoletoTalonario> candidatos = boletoTalonarioDao.buscarParaPuerta(
                DESTINO, tipoEvento, numero, EstadoRegistro.ACTIVO);
        if (candidatos.isEmpty()) {
            throw new RecursoNoEncontradoException("No existe el boleto " + numero
                    + " para " + tipoEvento.etiqueta() + " (" + DESTINO.etiqueta() + ")");
        }
        BoletoTalonario boleto = candidatos.get(0);
        var fecha = calendario.fechaDe(dia);
        if (fecha == null) {
            throw new NegocioException("El " + calendario.describir(dia)
                    + " no tiene fecha configurada");
        }
        Instant desde = fecha.atStartOfDay(calendario.zona()).toInstant();
        Instant hasta = fecha.plusDays(1).atStartOfDay(calendario.zona()).toInstant();
        if (movimientoTalonarioDao
                .existsByBoletoTalonarioIdBoletoTalonarioAndTipoAndEstadoAndFechaHoraBetween(
                        boleto.getIdBoletoTalonario(), TipoAcceso.ENTRADA,
                        EstadoRegistro.ACTIVO, desde, hasta)) {
            throw new NegocioException("El boleto " + numero + " (" + tipoEvento.etiqueta()
                    + ") ya tiene un ingreso registrado " + calendario.describir(dia));
        }

        Instant cuando = calendario.momentoEnDia(fecha);
        MovimientoTalonario mov = new MovimientoTalonario();
        mov.setBoletoTalonario(boleto);
        mov.setTipo(TipoAcceso.ENTRADA);
        mov.setFechaHora(cuando);
        movimientoTalonarioDao.save(mov);

        // El "dentro" solo se toca si el día regularizado es hoy.
        if (dia.equals(calendario.diaDeHoy())) {
            if (boleto.isDentro() && esDentroVencido(boleto)) boleto.setDentro(false);
            boleto.setDentro(true);
            boletoTalonarioDao.save(boleto);
        }

        log.info("Ingreso regularizado: boleto {} de {}/{}, {}.",
                numero, DESTINO, tipoEvento, calendario.describir(dia));
        ResultadoRegularizacionAccesoDto r = new ResultadoRegularizacionAccesoDto();
        r.setIdentificador(numero + " (" + tipoEvento.etiqueta() + ")");
        r.setDia(dia.name());
        r.setFechaHora(cuando);
        return r;
    }

    /** EVENTO_1/2/3 cae en DIA_1/2/3 (mismas fechas de la feria). */
    private DiaFeria diaDe(TipoTalonario tipoEvento) {
        return switch (tipoEvento) {
            case EVENTO_1 -> DiaFeria.DIA_1;
            case EVENTO_2 -> DiaFeria.DIA_2;
            case EVENTO_3 -> DiaFeria.DIA_3;
            case COMBO -> null;
        };
    }

    /** ¿El 'dentro' de este boleto quedó colgado de un día anterior? */
    private boolean esDentroVencido(BoletoTalonario boleto) {
        return movimientoTalonarioDao
                .findTopByBoletoTalonarioIdBoletoTalonarioOrderByFechaHoraDesc(
                        boleto.getIdBoletoTalonario())
                .map(m -> calendario.esDeUnDiaAnterior(m.getFechaHora()))
                // dentro=true sin ningún movimiento es incoherente: se limpia.
                .orElse(true);
    }

    private ValidacionTalonarioDto bloqueado(ValidacionTalonarioDto dto, String motivo, String mensaje) {
        dto.setBloqueado(true);
        dto.setMotivo(motivo);
        dto.setMensaje(mensaje);
        return dto;
    }
}
