package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.Utils.ticket.TicketRenderer;
import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Docente;
import com.uap.control_tickets.models.entity.Persona;
import com.uap.control_tickets.models.entity.Ticket;
import com.uap.control_tickets.models.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketDocenteTest {
    @Mock TicketDao ticketDao;
    @Mock EstudianteDao estudianteDao;
    @Mock AdministrativoDao administrativoDao;
    @Mock DocenteDao docenteDao;
    @Mock TicketRenderer renderer;
    @InjectMocks TicketServiceImpl service;

    @Test
    void emiteCodigoDocYReutilizaTicketExistente() {
        Persona persona = new Persona();
        persona.setNombre("María Pérez");
        persona.setCi("123456");
        Docente docente = new Docente();
        docente.setIdDocente(1L);
        docente.setPersona(persona);
        docente.setCodigoDocente("42");
        docente.setCarrera("Medicina");
        when(docenteDao.buscarParaCambio(1L)).thenReturn(Optional.of(docente));
        when(ticketDao.save(any())).thenAnswer(inv -> {
            Ticket ticket = inv.getArgument(0);
            ticket.setIdTicket(10L);
            when(ticketDao.findFirstByDocenteIdDocenteAndEstado(1L, EstadoRegistro.ACTIVO))
                    .thenReturn(Optional.of(ticket));
            return ticket;
        });
        var emitido = service.emitirDocente(1L);
        assertEquals("DOC-000001", emitido.getCodigoIdentificacion());
        assertEquals(CategoriaTicket.DOCENTE.name(), emitido.getCategoria());
        assertEquals("42", emitido.getCodigoDocente());
        assertEquals("Medicina", emitido.getCarrera());
        assertNotNull(emitido.getQrToken());
        assertEquals(emitido.getIdTicket(), service.emitirDocente(1L).getIdTicket());
        verify(ticketDao, times(1)).save(any());
    }
}
