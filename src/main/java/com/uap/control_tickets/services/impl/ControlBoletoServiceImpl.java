package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.control.BoletoDentroDto;
import com.uap.control_tickets.dto.control.EventoBoletoDto;
import com.uap.control_tickets.dto.control.ResumenBoletosDto;
import com.uap.control_tickets.dto.control.ValidacionBoletoDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Boleto;
import com.uap.control_tickets.models.entity.MovimientoBoleto;
import com.uap.control_tickets.models.entity.Ticket;
import com.uap.control_tickets.models.repository.BoletoDao;
import com.uap.control_tickets.models.repository.MovimientoBoletoDao;
import com.uap.control_tickets.models.repository.TicketDao;
import com.uap.control_tickets.services.interfaces.ControlBoletoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementacion del validador de boletos con escaneres dedicados.
 *
 * Flujo por validacion:
 *  1. Busca el boleto por codigo (solo los activos). 404 si no existe.
 *  2. Anti-clones: ENTRADA estando dentro, o SALIDA estando fuera, se rechazan
 *     con 409 + motivo (no se registra nada).
 *  3. Registra el movimiento (por el escaner dedicado) y actualiza Boleto.dentro.
 *  4. Publica el evento por SSE para que el monitoreo en vivo se entere al toque.
 *
 * Mismo patron que ControlServiceImpl (tickets de estudiante), sin la parte de
 * matricula/SIGSE: los boletos son anonimos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlBoletoServiceImpl implements ControlBoletoService {

    private final BoletoDao boletoDao;
    private final MovimientoBoletoDao movimientoBoletoDao;
    private final BoletoEventosPublisher eventos;
    private final CalendarioFeria calendario;
    private final TicketDao ticketDao;

    @Override
    @Transactional
    public ValidacionBoletoDto validar(String codigo, TipoAcceso tipoMovimiento) {
        String cod = codigo == null ? "" : codigo.trim();
        if (cod.isEmpty()) {
            throw new NegocioException("Ingrese el codigo del boleto");
        }
        if (tipoMovimiento == null) {
            throw new NegocioException("Indique el tipo de movimiento (ENTRADA o SALIDA)");
        }

        Boleto boleto = boletoDao.findByCodigo(cod)
                .filter(b -> b.getEstado() == EstadoRegistro.ACTIVO)
                .orElseGet(() -> {
                    eventos.publicar(evento("NO_VALIDO", cod, "NO_VALIDO", null));
                    throw new RecursoNoEncontradoException("Boleto no encontrado o no valido: " + cod);
                });

        ValidacionBoletoDto dto = new ValidacionBoletoDto();
        dto.setIdBoleto(boleto.getIdBoleto());
        dto.setCodigo(boleto.getCodigo());
        dto.setDentro(boleto.isDentro());
        BoletoServiceImpl.aplicarIdentificacion(boleto, dto::setCategoria, s -> { }, dto::setNombrePersona);
        if (boleto.getDiaFeria() != null) dto.setDiaFeria(boleto.getDiaFeria().name());

        // 'dentro' colgado de un dia anterior: el portador se fue sin escanear la
        // salida, hoy esta afuera. Si no se limpia, el anti-clones lo rechaza para
        // siempre y ademas ensucia el contador de "personas dentro".
        if (boleto.isDentro() && esDentroVencido(boleto)) {
            boleto.setDentro(false);
            dto.setDentro(false);
        }

        // El boleto es de un dia puntual de la feria: solo sirve ese dia.
        // Los boletos de venta suelta no tienen dia asignado y no se validan.
        if (tipoMovimiento == TipoAcceso.ENTRADA && calendario.validacionActiva()
                && boleto.getDiaFeria() != null) {
            var hoy = calendario.diaDeHoy();
            if (hoy == null) {
                dto.setBloqueado(true);
                dto.setMotivo("FUERA_DE_FECHA");
                dto.setMensaje("Hoy no es un día de la feria. Este boleto es para el "
                        + calendario.describir(boleto.getDiaFeria()) + ".");
                eventos.publicar(evento("BLOQUEADO", cod, "FUERA_DE_FECHA", boleto));
                return dto;
            }
            if (hoy != boleto.getDiaFeria()) {
                dto.setBloqueado(true);
                dto.setMotivo("DIA_INCORRECTO");
                dto.setMensaje("Este boleto es para el " + calendario.describir(boleto.getDiaFeria())
                        + " y hoy es " + calendario.describir(hoy) + ".");
                eventos.publicar(evento("BLOQUEADO", cod, "DIA_INCORRECTO", boleto));
                return dto;
            }
        }

        // Anti-clones: el escaner es dedicado, el estado tiene que coincidir.
        if (tipoMovimiento == TipoAcceso.ENTRADA && boleto.isDentro()) {
            dto.setBloqueado(true);
            dto.setMotivo("YA_DENTRO");
            dto.setMensaje("Este boleto ya se encuentra DENTRO del recinto (ENTRADA ya registrada).");
            eventos.publicar(evento("BLOQUEADO", cod, "YA_DENTRO", boleto));
            return dto;
        }
        if (tipoMovimiento == TipoAcceso.SALIDA && !boleto.isDentro()) {
            dto.setBloqueado(true);
            dto.setMotivo("YA_FUERA");
            dto.setMensaje("Este boleto no se encuentra DENTRO del recinto (no hay ENTRADA registrada).");
            eventos.publicar(evento("BLOQUEADO", cod, "YA_FUERA", boleto));
            return dto;
        }

        // Registrar el movimiento del escaner dedicado.
        boleto.setDentro(tipoMovimiento == TipoAcceso.ENTRADA);

        MovimientoBoleto mov = new MovimientoBoleto();
        mov.setBoleto(boleto);
        mov.setTipo(tipoMovimiento);
        mov.setFechaHora(Instant.now());
        movimientoBoletoDao.save(mov);
        boletoDao.save(boleto);

        dto.setDentro(boleto.isDentro());
        dto.setUltimoTipo(tipoMovimiento.name());
        dto.setUltimaFecha(mov.getFechaHora());
        if (boleto.isDentro()) dto.setEntrada(mov.getFechaHora());

        eventos.publicar(evento(tipoMovimiento.name(), cod, null, boleto));
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoletoDentroDto> boletosDentro() {
        Map<Long, Instant> entradas = new HashMap<>();
        movimientoBoletoDao.ultimasEntradasDentro(EstadoRegistro.ACTIVO)
                .forEach(u -> entradas.put(u.getIdBoleto(), u.getEntrada()));

        return boletoDao.findAllByEstado(EstadoRegistro.ACTIVO).stream()
                .filter(Boleto::isDentro)
                .map(b -> {
                    BoletoDentroDto dto = new BoletoDentroDto();
                    dto.setIdBoleto(b.getIdBoleto());
                    dto.setCodigo(b.getCodigo());
                    dto.setEntrada(entradas.get(b.getIdBoleto()));
                    BoletoServiceImpl.aplicarIdentificacion(b, dto::setCategoria, s -> { }, dto::setNombrePersona);
                    if (b.getDiaFeria() != null) dto.setDiaFeria(b.getDiaFeria().name());
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenBoletosDto resumen() {
        ResumenBoletosDto r = new ResumenBoletosDto();
        r.setDentro(boletoDao.countByDentroTrueAndEstado(EstadoRegistro.ACTIVO));
        r.setTotalBoletos(boletoDao.countByEstado(EstadoRegistro.ACTIVO));
        r.setIngresosTotal(movimientoBoletoDao.countByTipoAndEstado(TipoAcceso.ENTRADA, EstadoRegistro.ACTIVO));
        r.setSalidasTotal(movimientoBoletoDao.countByTipoAndEstado(TipoAcceso.SALIDA, EstadoRegistro.ACTIVO));

        // Contadores del DIA EN CURSO (el tablero en vivo no debe sumar los dias previos).
        var hoy = calendario.hoy();
        Instant desde = hoy.atStartOfDay(calendario.zona()).toInstant();
        Instant hasta = hoy.plusDays(1).atStartOfDay(calendario.zona()).toInstant();
        r.setIngresosHoy(movimientoBoletoDao.countByTipoAndEstadoAndFechaHoraBetween(
                TipoAcceso.ENTRADA, EstadoRegistro.ACTIVO, desde, hasta));
        r.setSalidasHoy(movimientoBoletoDao.countByTipoAndEstadoAndFechaHoraBetween(
                TipoAcceso.SALIDA, EstadoRegistro.ACTIVO, desde, hasta));
        var dia = calendario.diaDeHoy();
        r.setDiaHoy(dia == null ? null : dia.name());
        r.setFechaHoy(hoy.toString());
        // Desglose por categoría, para el monitoreo (Pulso FEXPO).
        r.setDentroAdministrativos(boletoDao.countByDentroTrueAndEstadoAndAdministrativoIsNotNull(EstadoRegistro.ACTIVO));
        r.setDentroDocentes(boletoDao.countByDentroTrueAndEstadoAndDocenteIsNotNull(EstadoRegistro.ACTIVO));
        r.setDentroParticulares(boletoDao.countByDentroTrueAndEstadoAndAdministrativoIsNullAndDocenteIsNull(EstadoRegistro.ACTIVO));
        return r;
    }

    /** ¿El 'dentro' de este boleto quedo colgado de un dia anterior? */
    private boolean esDentroVencido(Boleto boleto) {
        return movimientoBoletoDao
                .findTopByBoletoIdBoletoOrderByFechaHoraDesc(boleto.getIdBoleto())
                .map(m -> calendario.esDeUnDiaAnterior(m.getFechaHora()))
                // dentro=true sin ningun movimiento es un estado incoherente: se limpia.
                .orElse(true);
    }

    @Override
    @Transactional
    public int cerrarJornada() {
        int n = 0;
        List<Boleto> dentro = boletoDao.findAllByEstado(EstadoRegistro.ACTIVO).stream()
                .filter(Boleto::isDentro).toList();
        for (Boleto b : dentro) b.setDentro(false);
        boletoDao.saveAll(dentro);
        n += dentro.size();

        List<Ticket> tickets = ticketDao.findAllByDentroTrueAndEstado(EstadoRegistro.ACTIVO);
        for (Ticket t : tickets) t.setDentro(false);
        ticketDao.saveAll(tickets);
        n += tickets.size();

        log.info("Cierre de jornada: {} boletos y {} tickets quedaron fuera.",
                dentro.size(), tickets.size());
        eventos.publicar(evento("CIERRE_JORNADA", "", null, null));
        return n;
    }

    private EventoBoletoDto evento(String tipo, String codigo, String motivo, Boleto boleto) {
        EventoBoletoDto e = new EventoBoletoDto();
        e.setTipo(tipo);
        e.setCodigo(codigo);
        e.setMotivo(motivo);
        e.setFechaHora(Instant.now());
        e.setDentroAhora(boletoDao.countByDentroTrueAndEstado(EstadoRegistro.ACTIVO));
        if (boleto != null) {
            BoletoServiceImpl.aplicarIdentificacion(boleto, e::setCategoria, s -> { }, e::setNombrePersona);
            if (boleto.getDiaFeria() != null) e.setDiaFeria(boleto.getDiaFeria().name());
        }
        return e;
    }
}
