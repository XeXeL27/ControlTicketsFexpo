package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.apivalidacaion.Dto.ApiResponseDto;
import com.uap.control_tickets.apivalidacaion.Service.ApiService;
import com.uap.control_tickets.dto.control.MovimientoAccesoDto;
import com.uap.control_tickets.dto.control.DetalleIngresoConciertoDto;
import com.uap.control_tickets.dto.control.IngresosDiaConciertoDto;
import com.uap.control_tickets.dto.control.ReporteIngresosConciertoDto;
import com.uap.control_tickets.dto.control.ResultadoRegularizacionAccesoDto;
import com.uap.control_tickets.dto.control.PersonaDentroDto;
import com.uap.control_tickets.dto.control.ValidacionTicketDto;
import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.DiaFeria;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Acceso;
import com.uap.control_tickets.models.entity.Ticket;
import com.uap.control_tickets.models.repository.AccesoDao;
import com.uap.control_tickets.models.repository.MovimientoTalonarioDao;
import com.uap.control_tickets.models.repository.TicketDao;
import com.uap.control_tickets.services.interfaces.ControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementacion del validador de acceso con escaneres dedicados.
 *
 * Flujo por escaneo:
 *  1. Busca el ticket por qr_token (solo los activos). 404 si no existe.
 *  2. Determina internamente la categoria y los datos de la persona (BD local).
 *  3. Anti-clones: ENTRADA estando dentro, o SALIDA estando fuera, se rechazan
 *     con 409 + motivo (no se registra nada).
 *  4. Solo al ENTRAR un estudiante: valida la matricula con el RU. Solo una
 *     respuesta EXPLICITA de no-matriculado bloquea; si SIGSE no responde a
 *     tiempo se deja entrar con los datos locales (fail-open, la fila manda)
 *     y se avisa en el mensaje + log.
 *  5. Registra el movimiento (por el escaner dedicado) y actualiza el flag
 *     Ticket.dentro.
 *
 * Nada de lo que devuelve la consulta se guarda en BD.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlServiceImpl implements ControlService {

    private final TicketDao ticketDao;
    private final AccesoDao accesoDao;
    private final MovimientoTalonarioDao movimientoTalonarioDao;
    private final ApiService apiService;
    private final CalendarioFeria calendario;

    @Override
    @Transactional
    public ValidacionTicketDto validar(String codigo, TipoAcceso tipoMovimiento) {
        String qr = codigo == null ? "" : codigo.trim();
        if (qr.isEmpty()) {
            throw new NegocioException("Ingrese el codigo del ticket");
        }
        if (tipoMovimiento == null) {
            throw new NegocioException("Indique el tipo de movimiento (ENTRADA o SALIDA)");
        }

        Ticket ticket = ticketDao.buscarParaControl(qr)
                .filter(t -> t.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Ticket no encontrado o no valido: " + qr));

        ValidacionTicketDto dto = armarRespuestaConDatosLocales(ticket);

        // El ticket vale las tres noches. Si el flag 'dentro' quedo en true de un dia
        // ANTERIOR, la persona se fue sin escanear la salida: hoy esta afuera. Sin esto
        // el anti-clones la rechazaria con YA_DENTRO y no podria entrar nunca mas.
        if (ticket.isDentro() && esDentroVencido(ticket)) {
            ticket.setDentro(false);
            dto.setDentro(false);
        }

        // Anti-clones: el escaner es dedicado, el estado tiene que coincidir.
        if (tipoMovimiento == TipoAcceso.ENTRADA && ticket.isDentro()) {
            dto.setBloqueado(true);
            dto.setMotivo("YA_DENTRO");
            dto.setMensaje(
                    "La persona ya se encuentra DENTRO del recinto (ENTRADA ya registrada).");
            return dto;
        }
        if (tipoMovimiento == TipoAcceso.SALIDA && !ticket.isDentro()) {
            dto.setBloqueado(true);
            dto.setMotivo("YA_FUERA");
            dto.setMensaje(
                    "La persona no se encuentra DENTRO del recinto (no hay ENTRADA registrada).");
            return dto;
        }

        // Matricula: solo estudiantes al ENTRAR.
        //  - SIGSE dice NO matriculado (respuesta explícita) → se bloquea.
        //  - SIGSE no responde (lento o caído) → se DEJA ENTRAR con los datos
        //    locales y se avisa en el mensaje. En la puerta la fila no puede
        //    esperar: frenar a todos porque el sistema externo tarda es peor
        //    que dejar pasar a uno con el ticket válido de la BD local.
        if (tipoMovimiento == TipoAcceso.ENTRADA
                && ticket.getCategoria() == CategoriaTicket.ESTUDIANTE) {
            ApiResponseDto sigse = consultarSigse(dto.getRu());
            dto.setSigse(sigse);
            dto.setMatriculado(sigse != null && sigse.getData() != null
                    ? sigse.getData().isEstadoMatriculacion() : null);

            if (Boolean.FALSE.equals(dto.getMatriculado())) {
                dto.setBloqueado(true);
                dto.setMotivo("NO_MATRICULADO");
                dto.setMensaje("Estudiante no matriculado. No se permite el ingreso.");
                return dto;
            }
            if (dto.getMatriculado() == null) {
                log.warn("SIGSE sin respuesta para RU {}: ingreso con datos locales", dto.getRu());
                dto.setMensaje("Ingreso validado con datos locales "
                        + "(el sistema de matrícula no respondió a tiempo).");
            }
        }

        // Registrar el movimiento del escaner dedicado.
        ticket.setDentro(tipoMovimiento == TipoAcceso.ENTRADA);

        Acceso acceso = new Acceso();
        acceso.setTicket(ticket);
        acceso.setTipo(tipoMovimiento);
        acceso.setFechaHora(Instant.now());
        accesoDao.save(acceso);
        ticketDao.save(ticket);

        dto.setDentro(ticket.isDentro());
        dto.setUltimoMovimiento(new MovimientoAccesoDto(tipoMovimiento.name(), acceso.getFechaHora()));
        if (ticket.isDentro()) {
            dto.setEntrada(acceso.getFechaHora());
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<PersonaDentroDto> personasDentro() {
        List<Ticket> dentro = ticketDao.findAllByDentroTrueAndEstado(EstadoRegistro.ACTIVO);
        List<PersonaDentroDto> lista = new ArrayList<>(dentro.size());
        var entradas = accesoDao.ultimasEntradasDentro(EstadoRegistro.ACTIVO).stream()
                .collect(java.util.stream.Collectors.toMap(AccesoDao.UltimaEntrada::getIdTicket,
                        AccesoDao.UltimaEntrada::getEntrada));

        for (Ticket ticket : dentro) {
            PersonaDentroDto p = new PersonaDentroDto();
            p.setIdTicket(ticket.getIdTicket());
            p.setIdPersona(ticket.getPersona().getIdPersona());
            p.setCodigoIdentificacion(ticket.getCodigoIdentificacion());
            p.setCategoria(ticket.getCategoria().name());
            p.setNombreCompleto(ticket.getPersona().getNombreCompleto());
            p.setCi(ticket.getPersona().getCi());
            // Si esta dentro, su ultimo movimiento fue una ENTRADA.
            p.setEntrada(entradas.get(ticket.getIdTicket()));
            lista.add(p);
        }
        return lista;
    }

    @Override
    @Transactional(readOnly = true)
    public ReporteIngresosConciertoDto reporteIngresosPorDia() {
        // Un elemento por día del evento, en orden DIA_1 → DIA_3. Cada día se
        // cuenta por FECHA calendario del escaneo (zona del evento), no por
        // categoría: el ticket vale las tres noches y puede sumar varios días.
        List<IngresosDiaConciertoDto> dias = new ArrayList<>();
        long totalEstudiantes = 0;
        long totalAdministrativos = 0;
        long totalDocentes = 0;
        long totalParticulares = 0;
        for (DiaFeria dia : DiaFeria.values()) {
            var fecha = calendario.fechaDe(dia);
            IngresosDiaConciertoDto d = new IngresosDiaConciertoDto();
            d.setDia(dia.name());
            d.setFecha(fecha);
            if (fecha != null) {
                Instant desde = fecha.atStartOfDay(calendario.zona()).toInstant();
                Instant hasta = fecha.plusDays(1).atStartOfDay(calendario.zona()).toInstant();
                d.setIngresosEstudiantes(accesoDao.contarPorCategoria(
                        TipoAcceso.ENTRADA, EstadoRegistro.ACTIVO,
                        CategoriaTicket.ESTUDIANTE, desde, hasta));
                d.setIngresosAdministrativos(accesoDao.contarPorCategoria(
                        TipoAcceso.ENTRADA, EstadoRegistro.ACTIVO,
                        CategoriaTicket.ADMINISTRATIVO, desde, hasta));
                d.setIngresosDocentes(accesoDao.contarPorCategoria(
                        TipoAcceso.ENTRADA, EstadoRegistro.ACTIVO,
                        CategoriaTicket.DOCENTE, desde, hasta));
                // Particulares = tickets EXTERNO (acceso, hoy casi siempre 0:
                // aún no hay emisión de particular) MÁS la puerta de
                // talonarios del concierto (/control-talonarios), que es por
                // donde entra el particular con su papel numerado sin QR.
                // Esa puerta solo existe con destino CONCIERTO, así que todo
                // movimiento_talonario de ese día es ingreso al concierto.
                long particularesQr = accesoDao.contarPorCategoria(
                        TipoAcceso.ENTRADA, EstadoRegistro.ACTIVO,
                        CategoriaTicket.EXTERNO, desde, hasta);
                long particularesTalonario = movimientoTalonarioDao
                        .countByTipoAndEstadoAndFechaHoraBetween(
                                TipoAcceso.ENTRADA, EstadoRegistro.ACTIVO, desde, hasta);
                d.setIngresosParticulares(particularesQr + particularesTalonario);
            }
            d.setIngresosTotal(d.getIngresosEstudiantes() + d.getIngresosAdministrativos()
                    + d.getIngresosDocentes() + d.getIngresosParticulares());
            totalEstudiantes += d.getIngresosEstudiantes();
            totalAdministrativos += d.getIngresosAdministrativos();
            totalDocentes += d.getIngresosDocentes();
            totalParticulares += d.getIngresosParticulares();
            dias.add(d);
        }
        ReporteIngresosConciertoDto r = new ReporteIngresosConciertoDto();
        r.setDias(dias);
        r.setTotalEstudiantes(totalEstudiantes);
        r.setTotalAdministrativos(totalAdministrativos);
        r.setTotalDocentes(totalDocentes);
        r.setTotalParticulares(totalParticulares);
        r.setTotalGeneral(totalEstudiantes + totalAdministrativos + totalDocentes + totalParticulares);
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DetalleIngresoConciertoDto> detalleIngresos(DiaFeria dia, CategoriaTicket categoria) {
        // Días a cubrir: el pedido o los 3 (solo los que tengan fecha
        // configurada). Se consulta un rango por día porque las fechas del
        // evento no tienen por qué ser consecutivas.
        List<DiaFeria> dias = dia != null
                ? List.of(dia)
                : List.of(DiaFeria.values());
        Map<Long, DetalleIngresoConciertoDto> porTicket = new LinkedHashMap<>();
        for (DiaFeria d : dias) {
            var fecha = calendario.fechaDe(d);
            if (fecha == null) continue;
            Instant desde = fecha.atStartOfDay(calendario.zona()).toInstant();
            Instant hasta = fecha.plusDays(1).atStartOfDay(calendario.zona()).toInstant();
            for (Acceso a : accesoDao.entradasConTicketEnRango(
                    TipoAcceso.ENTRADA, EstadoRegistro.ACTIVO, desde, hasta)) {
                Ticket t = a.getTicket();
                if (t == null || (categoria != null && t.getCategoria() != categoria)) continue;
                DetalleIngresoConciertoDto fila = porTicket.get(t.getIdTicket());
                if (fila == null) {
                    fila = new DetalleIngresoConciertoDto();
                    fila.setIdTicket(t.getIdTicket());
                    fila.setCodigoIdentificacion(t.getCodigoIdentificacion());
                    fila.setCategoria(t.getCategoria().name());
                    fila.setNombreCompleto(t.getPersona().getNombreCompleto());
                    fila.setCi(t.getPersona().getCi());
                    if (t.getCategoria() == CategoriaTicket.ESTUDIANTE && t.getEstudiante() != null) {
                        fila.setCodigo(t.getEstudiante().getRu());
                        fila.setCarrera(t.getEstudiante().getCarrera());
                    } else if (t.getCategoria() == CategoriaTicket.ADMINISTRATIVO
                            && t.getAdministrativo() != null) {
                        fila.setCodigo(t.getAdministrativo().getCodigoAdministrativo());
                    } else if (t.getCategoria() == CategoriaTicket.DOCENTE && t.getDocente() != null) {
                        fila.setCodigo(t.getDocente().getCodigoDocente());
                        fila.setCarrera(t.getDocente().getCarrera());
                    }
                    fila.setUltimaEntrada(a.getFechaHora());
                    porTicket.put(t.getIdTicket(), fila);
                }
                fila.setEntradas(fila.getEntradas() + 1);
                if (a.getFechaHora() != null && (fila.getUltimaEntrada() == null
                        || a.getFechaHora().isAfter(fila.getUltimaEntrada()))) {
                    fila.setUltimaEntrada(a.getFechaHora());
                }
            }
        }
        return porTicket.values().stream()
                .sorted(java.util.Comparator.comparing(
                        DetalleIngresoConciertoDto::getNombreCompleto,
                        java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Override
    @Transactional
    public ResultadoRegularizacionAccesoDto regularizarIngreso(String codigo, DiaFeria dia) {
        String qr = codigo == null ? "" : codigo.trim();
        if (qr.isEmpty()) {
            throw new NegocioException("Ingrese el código del ticket");
        }
        if (dia == null) {
            throw new NegocioException("Indique el día del ingreso");
        }
        Ticket ticket = ticketDao.buscarParaControl(qr)
                .filter(t -> t.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Ticket no encontrado o no válido: " + qr));
        var fecha = calendario.fechaDe(dia);
        if (fecha == null) {
            throw new NegocioException("El " + calendario.describir(dia)
                    + " no tiene fecha configurada");
        }
        Instant desde = fecha.atStartOfDay(calendario.zona()).toInstant();
        Instant hasta = fecha.plusDays(1).atStartOfDay(calendario.zona()).toInstant();
        if (accesoDao.existsByTicketIdTicketAndTipoAndEstadoAndFechaHoraBetween(
                ticket.getIdTicket(), TipoAcceso.ENTRADA, EstadoRegistro.ACTIVO, desde, hasta)) {
            throw new NegocioException("El ticket " + ticket.getCodigoIdentificacion()
                    + " ya tiene un ingreso registrado " + calendario.describir(dia));
        }

        Instant cuando = calendario.momentoEnDia(fecha);
        Acceso acceso = new Acceso();
        acceso.setTicket(ticket);
        acceso.setTipo(TipoAcceso.ENTRADA);
        acceso.setFechaHora(cuando);
        accesoDao.save(acceso);

        // El "dentro" solo se toca si el día regularizado es hoy: un ingreso de
        // un día pasado no significa que la persona esté adentro ahora.
        if (dia.equals(calendario.diaDeHoy())) {
            if (ticket.isDentro() && esDentroVencido(ticket)) ticket.setDentro(false);
            ticket.setDentro(true);
            ticketDao.save(ticket);
        }

        log.info("Ingreso regularizado: ticket {} {}.",
                ticket.getCodigoIdentificacion(), calendario.describir(dia));
        ResultadoRegularizacionAccesoDto r = new ResultadoRegularizacionAccesoDto();
        r.setIdentificador(ticket.getCodigoIdentificacion());
        r.setDia(dia.name());
        r.setFechaHora(cuando);
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseDto consultarSigse(Integer ru) {
        if (ru == null) {
            throw new NegocioException("Indique el RU del estudiante");
        }
        try {
            ApiResponseDto res = apiService.informacion(ru);
            if (res == null) {
                throw new NegocioException("El sistema de matricula no respondio a la consulta del RU " + ru);
            }
            return res;
        } catch (NegocioException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error consultando la matricula para RU {}: {}", ru, e.getMessage());
            throw new NegocioException("El sistema de matricula no responde: " + e.getMessage());
        }
    }

    // ---------------- helpers ----------------

    /**
     * ¿El 'dentro' de este ticket es de un dia anterior (quedo colgado)?
     * Se mira el ultimo movimiento registrado: si fue ayer o antes, el flag esta viejo.
     */
    private boolean esDentroVencido(Ticket ticket) {
        return accesoDao.findTopByTicketIdTicketOrderByFechaHoraDesc(ticket.getIdTicket())
                .map(a -> calendario.esDeUnDiaAnterior(a.getFechaHora()))
                // Sin movimientos pero con dentro=true: es un estado incoherente, se limpia.
                .orElse(true);
    }

    /** Carga nombre, CI y los datos propios segun la categoria (todo de BD local). */
    private ValidacionTicketDto armarRespuestaConDatosLocales(Ticket ticket) {
        ValidacionTicketDto dto = new ValidacionTicketDto();
        dto.setIdTicket(ticket.getIdTicket());
        dto.setCategoria(ticket.getCategoria().name());
        dto.setCodigoIdentificacion(ticket.getCodigoIdentificacion());
        dto.setDentro(ticket.isDentro());
        dto.setNombreCompleto(ticket.getPersona().getNombreCompleto());
        dto.setCi(ticket.getPersona().getCi());

        if (ticket.getCategoria() == CategoriaTicket.ESTUDIANTE && ticket.getEstudiante() != null) {
            dto.setRu(ticket.getEstudiante().getRu());
            dto.setCarrera(ticket.getEstudiante().getCarrera());
            dto.setFacultad(ticket.getEstudiante().getFacultad());
        } else if (ticket.getCategoria() == CategoriaTicket.ADMINISTRATIVO
                && ticket.getAdministrativo() != null) {
            dto.setCodigoAdministrativo(ticket.getAdministrativo().getCodigoAdministrativo());
        } else if (ticket.getCategoria() == CategoriaTicket.DOCENTE && ticket.getDocente() != null) {
            dto.setCodigoDocente(ticket.getDocente().getCodigoDocente());
            dto.setCarrera(ticket.getDocente().getCarrera());
        }
        return dto;
    }

    /** Consulta la matricula por RU. Devuelve null si la API no responde o el RU no es numerico. */
    private ApiResponseDto consultarSigse(String ru) {
        try {
            int ruInt = Integer.parseInt(ru == null ? "" : ru.trim());
            return apiService.informacion(ruInt);
        } catch (NumberFormatException e) {
            log.warn("RU no numerico para la consulta de matricula: {}", ru);
            return null;
        } catch (Exception e) {
            log.error("Error consultando la matricula para RU {}: {}", ru, e.getMessage());
            return null;
        }
    }
}
