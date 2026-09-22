package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.reporte.ResumenEntregasDto;
import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.repository.TicketDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Reporte de entregas: tickets totales vs entregados por categoría.
 * Solo cuenta tickets en estado ACTIVO (borrado lógico excluido).
 */
@Service
@RequiredArgsConstructor
public class ReporteEntregasService {

    private final TicketDao ticketDao;

    @Transactional(readOnly = true)
    public ResumenEntregasDto resumen() {
        ResumenEntregasDto dto = new ResumenEntregasDto();
        dto.setGeneradoEn(Instant.now());

        dto.setEstudiantes(categoria(CategoriaTicket.ESTUDIANTE, "Estudiantes"));
        dto.setAdministrativos(categoria(CategoriaTicket.ADMINISTRATIVO, "Administrativos"));
        dto.setDocentes(categoria(CategoriaTicket.DOCENTE, "Docentes"));

        long totalTickets = dto.getEstudiantes().getTotal()
                + dto.getAdministrativos().getTotal()
                + dto.getDocentes().getTotal();
        long totalEntregados = dto.getEstudiantes().getEntregados()
                + dto.getAdministrativos().getEntregados()
                + dto.getDocentes().getEntregados();
        long pendientes = totalTickets - totalEntregados;
        double pct = totalTickets == 0 ? 0 : (totalEntregados * 100.0 / totalTickets);

        ResumenEntregasDto.CategoriaEntrega total = new ResumenEntregasDto.CategoriaEntrega();
        total.setCategoria("TOTAL");
        total.setEtiqueta("Total");
        total.setTotal(totalTickets);
        total.setEntregados(totalEntregados);
        total.setPendientes(pendientes);
        total.setPorcentaje(pct);
        dto.setTotal(total);

        return dto;
    }

    private ResumenEntregasDto.CategoriaEntrega categoria(CategoriaTicket cat, String etiqueta) {
        long total = ticketDao.countByCategoriaAndEstado(cat, EstadoRegistro.ACTIVO);
        long entregados = ticketDao.countByCategoriaAndEntregadoAndEstado(cat, true, EstadoRegistro.ACTIVO);
        long pendientes = total - entregados;
        double pct = total == 0 ? 0 : (entregados * 100.0 / total);

        ResumenEntregasDto.CategoriaEntrega c = new ResumenEntregasDto.CategoriaEntrega();
        c.setCategoria(cat.name());
        c.setEtiqueta(etiqueta);
        c.setTotal(total);
        c.setEntregados(entregados);
        c.setPendientes(pendientes);
        c.setPorcentaje(pct);
        return c;
    }
}
