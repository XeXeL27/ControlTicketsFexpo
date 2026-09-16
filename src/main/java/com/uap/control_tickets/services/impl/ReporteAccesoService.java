package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.control.HistorialPersonaDto;
import com.uap.control_tickets.dto.control.ReportePersonaDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.repository.AccesoDao;
import com.uap.control_tickets.models.repository.TicketDao;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReporteAccesoService {
    private final TicketDao ticketDao;
    private final AccesoDao accesoDao;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ReportePersonaDto> listar() {
        var personas = new LinkedHashMap<Long, ReportePersonaDto>();
        // Incluir tickets antiguos para conservar el historial de una persona.
        // Solo los tickets activos determinan si está dentro actualmente.
        for (var ticket : ticketDao.ticketsParaReporte()) {
            var persona = ticket.getPersona();
            var reporte = personas.computeIfAbsent(persona.getIdPersona(), id -> {
                var nuevo = new ReportePersonaDto();
                nuevo.setIdPersona(id);
                nuevo.setNombreCompleto(persona.getNombreCompleto());
                nuevo.setCi(persona.getCi());
                return nuevo;
            });
            String categoria = ticket.getCategoria().name();
            if (!reporte.getCategorias().contains(categoria)) reporte.getCategorias().add(categoria);
            reporte.getCodigos().add(ticket.getCodigoIdentificacion());
            if (ticket.getEstado() == EstadoRegistro.ACTIVO && ticket.isDentro()) reporte.setDentro(true);
        }
        for (var total : accesoDao.totalesPorPersona(EstadoRegistro.ACTIVO)) {
            var reporte = personas.get(total.getIdPersona());
            if (reporte == null) continue;
            reporte.setEntradas(total.getEntradas());
            reporte.setSalidas(total.getSalidas());
            reporte.setUltimoMovimiento(total.getUltimoMovimiento());
        }
        return personas.values().stream().sorted(Comparator.comparing(
                ReportePersonaDto::getNombreCompleto, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HistorialPersonaDto historial(Long idPersona, int pagina) {
        var movimientos = accesoDao.findAllByTicketPersonaIdPersonaAndEstadoOrderByFechaHoraDescIdAccesoDesc(
                idPersona, EstadoRegistro.ACTIVO, PageRequest.of(Math.max(0, pagina), 50));
        return new HistorialPersonaDto(movimientos.getContent().stream().map(a ->
                new HistorialPersonaDto.Movimiento(a.getIdAcceso(), a.getTipo().name(), a.getFechaHora(),
                        a.getTicket().getCodigoIdentificacion(), a.getTicket().getCategoria().name())).toList(),
                movimientos.getTotalElements(), movimientos.getNumber(), movimientos.getTotalPages());
    }
}
