package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.apivalidacaion.Dto.ApiResponseDto;
import com.uap.control_tickets.apivalidacaion.Service.ApiService;
import com.uap.control_tickets.dto.control.MovimientoAccesoDto;
import com.uap.control_tickets.dto.control.PersonaDentroDto;
import com.uap.control_tickets.dto.control.ValidacionTicketDto;
import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Acceso;
import com.uap.control_tickets.models.entity.Ticket;
import com.uap.control_tickets.models.repository.AccesoDao;
import com.uap.control_tickets.models.repository.TicketDao;
import com.uap.control_tickets.services.interfaces.ControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementacion del validador de acceso con escaneres dedicados.
 *
 * Flujo por escaneo:
 *  1. Busca el ticket por qr_token (solo los activos). 404 si no existe.
 *  2. Determina internamente la categoria y los datos de la persona (BD local).
 *  3. Anti-clones: ENTRADA estando dentro, o SALIDA estando fuera, se rechazan
 *     con error de negocio (no se registra nada).
 *  4. Solo al ENTRAR un estudiante: consulta SIGSE con el RU; si no esta
 *     matriculado bloquea el ingreso (no persiste).
 *  5. Registra el movimiento (por el escaner dedicado) y actualiza el flag
 *     Ticket.dentro.
 *
 * Nada de lo que devuelve SIGSE se guarda en BD.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlServiceImpl implements ControlService {

    private final TicketDao ticketDao;
    private final AccesoDao accesoDao;
    private final ApiService apiService;

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

        Ticket ticket = ticketDao.findByQrToken(qr)
                .filter(t -> t.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Ticket no encontrado o no valido: " + qr));

        ValidacionTicketDto dto = armarRespuestaConDatosLocales(ticket);

        // Anti-clones: el escaner es dedicado, el estado tiene que coincidir.
        if (tipoMovimiento == TipoAcceso.ENTRADA && ticket.isDentro()) {
            throw new NegocioException(
                    "La persona ya se encuentra DENTRO del recinto (ENTRADA ya registrada).");
        }
        if (tipoMovimiento == TipoAcceso.SALIDA && !ticket.isDentro()) {
            throw new NegocioException(
                    "La persona no se encuentra DENTRO del recinto (no hay ENTRADA registrada).");
        }

        // SIGSE: solo estudiantes al ENTRAR. Determina si el ingreso esta permitido.
        if (tipoMovimiento == TipoAcceso.ENTRADA
                && ticket.getCategoria() == CategoriaTicket.ESTUDIANTE) {
            ApiResponseDto sigse = consultarSigse(dto.getRu());
            dto.setSigse(sigse);
            dto.setMatriculado(sigse != null && sigse.getData() != null
                    && sigse.getData().isEstadoMatriculacion());

            // Entrada bloqueada si no se puede confirmar la matricula.
            if (Boolean.FALSE.equals(dto.getMatriculado())) {
                dto.setBloqueado(true);
                dto.setMensaje("Estudiante no matriculado (o sin confirmacion SIGSE). No se permite el ingreso.");
                return dto;
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
    @Transactional(readOnly = true)
    public List<PersonaDentroDto> personasDentro() {
        List<Ticket> dentro = ticketDao.findAllByDentroTrueAndEstado(EstadoRegistro.ACTIVO);
        List<PersonaDentroDto> lista = new ArrayList<>(dentro.size());

        for (Ticket ticket : dentro) {
            PersonaDentroDto p = new PersonaDentroDto();
            p.setIdTicket(ticket.getIdTicket());
            p.setCodigoIdentificacion(ticket.getCodigoIdentificacion());
            p.setCategoria(ticket.getCategoria().name());
            p.setNombreCompleto(ticket.getPersona().getNombreCompleto());
            p.setCi(ticket.getPersona().getCi());
            // Si esta dentro, su ultimo movimiento fue una ENTRADA.
            accesoDao.findTopByTicketIdTicketOrderByFechaHoraDesc(ticket.getIdTicket())
                    .ifPresent(a -> p.setEntrada(a.getFechaHora()));
            lista.add(p);
        }
        return lista;
    }

    // ---------------- helpers ----------------

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
        }
        return dto;
    }

    /** Consulta SIGSE por RU. Devuelve null si la API no responde o el RU no es numerico. */
    private ApiResponseDto consultarSigse(String ru) {
        try {
            int ruInt = Integer.parseInt(ru == null ? "" : ru.trim());
            return apiService.informacion(ruInt);
        } catch (NumberFormatException e) {
            log.warn("RU no numerico para SIGSE: {}", ru);
            return null;
        } catch (Exception e) {
            log.error("Error consultando SIGSE para RU {}: {}", ru, e.getMessage());
            return null;
        }
    }
}