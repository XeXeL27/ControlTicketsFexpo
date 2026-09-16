package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.enums.*;
import com.uap.control_tickets.models.entity.*;
import com.uap.control_tickets.models.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReporteAccesoServiceTest {
    @Mock TicketDao ticketDao;
    @Mock AccesoDao accesoDao;
    @InjectMocks ReporteAccesoService service;

    private Ticket ticket(Long idPersona, String codigo, boolean dentro, EstadoRegistro estado) {
        Persona persona = new Persona();
        persona.setIdPersona(idPersona);
        persona.setNombre("Persona " + idPersona);
        persona.setCi("CI-" + idPersona);
        Ticket ticket = new Ticket();
        ticket.setPersona(persona);
        ticket.setCategoria(CategoriaTicket.DOCENTE);
        ticket.setCodigoIdentificacion(codigo);
        ticket.setDentro(dentro);
        ticket.setEstado(estado);
        return ticket;
    }

    @Test
    void agrupaTicketsDeUnaPersonaYUsaTotalesHistoricos() {
        when(ticketDao.ticketsParaReporte()).thenReturn(List.of(
                ticket(1L, "DOC-1", false, EstadoRegistro.ACTIVO),
                ticket(1L, "DOC-2", true, EstadoRegistro.ACTIVO)));
        var total = mock(AccesoDao.TotalesPersona.class);
        when(total.getIdPersona()).thenReturn(1L);
        when(total.getEntradas()).thenReturn(5L);
        when(total.getSalidas()).thenReturn(4L);
        when(total.getUltimoMovimiento()).thenReturn(Instant.parse("2026-09-15T20:00:00Z"));
        when(accesoDao.totalesPorPersona(EstadoRegistro.ACTIVO)).thenReturn(List.of(total));
        var reportes = service.listar();
        assertEquals(1, reportes.size());
        var reporte = reportes.getFirst();
        assertEquals(5, reporte.getEntradas());
        assertEquals(4, reporte.getSalidas());
        assertTrue(reporte.isDentro());
        assertEquals(List.of("DOC-1", "DOC-2"), reporte.getCodigos());
        assertEquals(List.of("DOCENTE"), reporte.getCategorias());
    }

    @Test
    void sinMovimientosTieneCerosYTicketEliminadoNoDeterminaEstado() {
        when(ticketDao.ticketsParaReporte()).thenReturn(List.of(
                ticket(1L, "DOC-1", true, EstadoRegistro.ELIMINADO),
                ticket(2L, "DOC-2", false, EstadoRegistro.ACTIVO)));
        var reportes = service.listar();
        assertEquals(2, reportes.size());
        for (var reporte : reportes) {
            assertEquals(0, reporte.getEntradas());
            assertEquals(0, reporte.getSalidas());
            assertNull(reporte.getUltimoMovimiento());
            assertFalse(reporte.isDentro());
        }
    }

    @Test
    void historialConservaFechaTipoYTicketYPaginaEnBloquesDe50() {
        Acceso acceso = new Acceso();
        acceso.setIdAcceso(10L);
        acceso.setTicket(ticket(1L, "DOC-1", true, EstadoRegistro.ACTIVO));
        acceso.setTipo(TipoAcceso.ENTRADA);
        acceso.setFechaHora(Instant.parse("2026-09-15T20:00:00Z"));
        when(accesoDao.findAllByTicketPersonaIdPersonaAndEstadoOrderByFechaHoraDescIdAccesoDesc(
                eq(1L), eq(EstadoRegistro.ACTIVO), any()))
                .thenReturn(new PageImpl<>(List.of(acceso), PageRequest.of(1, 50), 51));
        var reporte = service.historial(1L, 1);
        assertEquals(51, reporte.total());
        assertEquals(2, reporte.paginas());
        assertEquals(1, reporte.pagina());
        assertEquals("DOC-1", reporte.movimientos().getFirst().codigoTicket());
        assertEquals("ENTRADA", reporte.movimientos().getFirst().tipo());
        assertEquals(acceso.getFechaHora(), reporte.movimientos().getFirst().fechaHora());
        verify(accesoDao).findAllByTicketPersonaIdPersonaAndEstadoOrderByFechaHoraDescIdAccesoDesc(
                1L, EstadoRegistro.ACTIVO, PageRequest.of(1, 50));
    }
}
