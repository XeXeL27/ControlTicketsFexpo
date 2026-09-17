package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.persona.CambioTipoDto;
import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.*;
import com.uap.control_tickets.models.repository.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CambioTipoServiceTest {
    @Mock AdministrativoDao administrativoDao;
    @Mock DocenteDao docenteDao;
    @Mock TicketDao ticketDao;
    @InjectMocks CambioTipoService service;

    private Administrativo administrativo() {
        var persona = new Persona();
        persona.setIdPersona(10L);
        persona.setNombre("Ana");
        persona.setCi("12345");
        var a = new Administrativo();
        a.setIdAdministrativo(1L);
        a.setCodigoAdministrativo("42");
        a.setPersona(persona);
        return a;
    }

    @Test
    void idaYVueltaConservaTicketImpresoEntregadoDentroYReferencias() {
        var a = administrativo();
        var t = new Ticket();
        t.setIdTicket(7L);
        t.setPersona(a.getPersona());
        t.setAdministrativo(a);
        t.setCategoria(CategoriaTicket.ADMINISTRATIVO);
        t.setCodigoIdentificacion("ADM-000007");
        t.setQrToken("qr-original");
        t.setImpreso(true);
        t.setEntregado(true);
        t.setDentro(true);
        var fecha = Instant.parse("2026-09-16T10:00:00Z");
        t.setFechaImpresion(fecha);
        t.setFechaEntrega(fecha);
        var acceso = new Acceso();
        acceso.setTicket(t);
        when(administrativoDao.buscarParaCambio(1L)).thenReturn(Optional.of(a));
        when(ticketDao.findAllByAdministrativoIdAdministrativo(1L)).thenReturn(List.of(t));
        when(docenteDao.save(any())).thenAnswer(inv -> {
            Docente d = inv.getArgument(0);
            d.setIdDocente(2L);
            return d;
        });

        service.aDocente(1L, " Sistemas ");
        var d = t.getDocente();
        assertEquals("42", d.getCodigoDocente());
        assertSame(a.getPersona(), d.getPersona());
        assertEquals("Sistemas", d.getCarrera());
        assertEquals(EstadoRegistro.ELIMINADO, a.getEstado());
        assertEquals(CategoriaTicket.DOCENTE, t.getCategoria());
        assertNull(t.getAdministrativo());

        when(docenteDao.buscarParaCambio(2L)).thenReturn(Optional.of(d));
        when(administrativoDao.findByCodigoAdministrativo("42")).thenReturn(Optional.of(a));
        when(ticketDao.findAllByDocenteIdDocente(2L)).thenReturn(List.of(t));
        service.aAdministrativo(2L);
        assertEquals(CategoriaTicket.ADMINISTRATIVO, t.getCategoria());
        assertSame(a, t.getAdministrativo());
        assertNull(t.getDocente());
        assertEquals(EstadoRegistro.ACTIVO, a.getEstado());
        assertEquals(EstadoRegistro.ELIMINADO, d.getEstado());
        assertEquals("Sistemas", d.getCarrera());

        when(docenteDao.findByCodigoDocente("42")).thenReturn(Optional.of(d));
        service.aDocente(1L, "");
        assertSame(d, t.getDocente());
        assertEquals("Sistemas", d.getCarrera());
        assertEquals(7L, t.getIdTicket());
        assertEquals("ADM-000007", t.getCodigoIdentificacion());
        assertEquals("qr-original", t.getQrToken());
        assertTrue(t.isImpreso());
        assertTrue(t.isEntregado());
        assertTrue(t.isDentro());
        assertEquals(fecha, t.getFechaImpresion());
        assertEquals(fecha, t.getFechaEntrega());
        assertEquals(EstadoRegistro.ACTIVO, t.getEstado());
        assertSame(t, acceso.getTicket());
        assertSame(a.getPersona(), t.getPersona());
        verify(ticketDao, never()).save(any());
    }

    @Test
    void requiereCarreraParaDocenteNuevoSinModificarOrigen() {
        var a = administrativo();
        when(administrativoDao.buscarParaCambio(1L)).thenReturn(Optional.of(a));
        assertThrows(NegocioException.class, () -> service.aDocente(1L, " "));
        assertEquals(EstadoRegistro.ACTIVO, a.getEstado());
        verify(docenteDao, never()).save(any());
        verifyNoInteractions(ticketDao);
    }

    @Test
    void rechazaCodigoDeOtraPersona() {
        var a = administrativo();
        var d = new Docente();
        d.setIdDocente(2L);
        var otra = new Persona();
        otra.setIdPersona(99L);
        d.setPersona(otra);
        when(administrativoDao.buscarParaCambio(1L)).thenReturn(Optional.of(a));
        when(docenteDao.findByCodigoDocente("42")).thenReturn(Optional.of(d));
        assertThrows(NegocioException.class, () -> service.aDocente(1L, "Sistemas"));
        verify(docenteDao, never()).save(any());
        verifyNoInteractions(ticketDao);
    }

    @Test
    void rechazaDestinoActivoYConversionRepetida() {
        var a = administrativo();
        when(administrativoDao.buscarParaCambio(1L)).thenReturn(Optional.of(a));
        when(docenteDao.findByPersonaIdPersonaAndEstado(10L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(new Docente()));
        assertThrows(NegocioException.class, () -> service.aDocente(1L, "Sistemas"));
        a.setEstado(EstadoRegistro.ELIMINADO);
        assertThrows(RecursoNoEncontradoException.class, () -> service.aDocente(1L, "Sistemas"));
        verifyNoInteractions(ticketDao);
    }

    @Test
    void docenteSinTicketPuedePasarAAdministrativoConMismoCodigo() {
        var d = new Docente();
        d.setIdDocente(2L);
        d.setPersona(administrativo().getPersona());
        d.setCodigoDocente("42");
        d.setCarrera("Sistemas");
        when(docenteDao.buscarParaCambio(2L)).thenReturn(Optional.of(d));
        service.aAdministrativo(2L);
        var captor = org.mockito.ArgumentCaptor.forClass(Administrativo.class);
        verify(administrativoDao).save(captor.capture());
        assertEquals("42", captor.getValue().getCodigoAdministrativo());
        assertSame(d.getPersona(), captor.getValue().getPersona());
        assertEquals("Sistemas", d.getCarrera());
        assertEquals(EstadoRegistro.ELIMINADO, d.getEstado());
    }

    @Test
    void solicitudExigeConfirmacionExplicita() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var dto = new CambioTipoDto();
            assertFalse(factory.getValidator().validate(dto).isEmpty());
            dto.setConfirmado(true);
            assertTrue(factory.getValidator().validate(dto).isEmpty());
        }
    }
}
