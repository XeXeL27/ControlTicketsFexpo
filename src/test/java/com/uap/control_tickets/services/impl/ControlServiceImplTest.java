package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.apivalidacaion.Dto.ApiResponseDto;
import com.uap.control_tickets.apivalidacaion.Dto.EstudianteDto;
import com.uap.control_tickets.apivalidacaion.Service.ApiService;
import com.uap.control_tickets.enums.*;
import com.uap.control_tickets.models.entity.*;
import com.uap.control_tickets.models.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControlServiceImplTest {
    @Mock TicketDao ticketDao;
    @Mock AccesoDao accesoDao;
    @Mock ApiService api;
    @Mock CalendarioFeria calendario;
    @InjectMocks ControlServiceImpl service;

    private Ticket ticket(CategoriaTicket categoria, boolean dentro) {
        Persona persona = new Persona();
        persona.setIdPersona(9L);
        persona.setNombre("Persona de prueba");
        persona.setCi("123");
        Ticket ticket = new Ticket();
        ticket.setIdTicket(1L);
        ticket.setPersona(persona);
        ticket.setCategoria(categoria);
        ticket.setDentro(dentro);
        ticket.setCodigoIdentificacion("PRUEBA-1");
        if (categoria == CategoriaTicket.ESTUDIANTE) {
            Estudiante estudiante = new Estudiante();
            estudiante.setRu("123");
            ticket.setEstudiante(estudiante);
        }
        return ticket;
    }

    /**
     * El ultimo movimiento fue HOY: el flag 'dentro' no esta vencido y el
     * anti-clones aplica normal (logica de jornada de ControlServiceImpl).
     * Sin esto el mock devuelve "sin movimientos" y el validador limpia el
     * 'dentro' por incoherente antes del anti-clones.
     */
    private void ultimoMovimientoHoy() {
        Acceso ultimo = new Acceso();
        ultimo.setFechaHora(Instant.now());
        when(accesoDao.findTopByTicketIdTicketOrderByFechaHoraDesc(1L))
                .thenReturn(Optional.of(ultimo));
        when(calendario.esDeUnDiaAnterior(any())).thenReturn(false);
    }

    @ParameterizedTest
    @EnumSource(value = CategoriaTicket.class, names = {"ADMINISTRATIVO", "DOCENTE", "EXTERNO"})
    void otrosTiposEntranYSalenSoloConBaseLocal(CategoriaTicket categoria) {
        Ticket ticket = ticket(categoria, false);
        when(ticketDao.buscarParaControl("PRUEBA-1")).thenReturn(Optional.of(ticket));
        ultimoMovimientoHoy();
        assertTrue(service.validar("PRUEBA-1", TipoAcceso.ENTRADA).isDentro());
        assertFalse(service.validar("PRUEBA-1", TipoAcceso.SALIDA).isDentro());
        verify(accesoDao, times(2)).save(any());
        verifyNoInteractions(api);
    }

    @Test
    void estudianteMatriculadoConsultaApiYEntra() {
        Ticket ticket = ticket(CategoriaTicket.ESTUDIANTE, false);
        when(ticketDao.buscarParaControl("qr")).thenReturn(Optional.of(ticket));
        EstudianteDto datos = new EstudianteDto();
        datos.setEstadoMatriculacion(true);
        when(api.informacion(123)).thenReturn(new ApiResponseDto(200, true, "OK", datos));
        var resultado = service.validar("qr", TipoAcceso.ENTRADA);
        assertTrue(resultado.isDentro());
        assertEquals(Boolean.TRUE, resultado.getMatriculado());
        verify(accesoDao).save(any());
    }

    @Test
    void sinConfirmacionSigseNoEntraPeroPuedeSalir() {
        Ticket ticket = ticket(CategoriaTicket.ESTUDIANTE, false);
        when(ticketDao.buscarParaControl("qr")).thenReturn(Optional.of(ticket));
        when(api.informacion(123)).thenReturn(null);
        var resultado = service.validar("qr", TipoAcceso.ENTRADA);
        assertTrue(resultado.isBloqueado());
        assertNull(resultado.getMatriculado());
        assertFalse(ticket.isDentro());
        verify(accesoDao, never()).save(any());
        ticket.setDentro(true);
        ultimoMovimientoHoy();
        assertFalse(service.validar("qr", TipoAcceso.SALIDA).isDentro());
        verify(accesoDao).save(any());
    }

    @Test
    void estudianteNoMatriculadoNoRegistraIngreso() {
        when(ticketDao.buscarParaControl("qr")).thenReturn(Optional.of(ticket(CategoriaTicket.ESTUDIANTE, false)));
        when(api.informacion(123)).thenReturn(new ApiResponseDto(200, true, "OK", new EstudianteDto()));
        var resultado = service.validar("qr", TipoAcceso.ENTRADA);
        assertTrue(resultado.isBloqueado());
        assertEquals(Boolean.FALSE, resultado.getMatriculado());
        verify(accesoDao, never()).save(any());
    }

    @Test
    void lecturaRepetidaNoInvierteEstadoNiDuplicaMovimiento() {
        when(ticketDao.buscarParaControl("qr")).thenReturn(Optional.of(ticket(CategoriaTicket.DOCENTE, false)));
        ultimoMovimientoHoy();
        service.validar("qr", TipoAcceso.ENTRADA);
        var repetida = service.validar("qr", TipoAcceso.ENTRADA);
        assertTrue(repetida.isDentro());
        assertTrue(repetida.isBloqueado());
        assertEquals("YA_DENTRO", repetida.getMotivo());
        assertNull(repetida.getUltimoMovimiento());
        assertNotNull(repetida.getMensaje());
        verify(accesoDao, times(1)).save(any());
    }

    @Test
    void salidaSinEntradaSeRechazaSinGuardarNiConsultarSigse() {
        Ticket ticket = ticket(CategoriaTicket.ESTUDIANTE, false);
        when(ticketDao.buscarParaControl("qr")).thenReturn(Optional.of(ticket));
        var resultado = service.validar("qr", TipoAcceso.SALIDA);
        assertTrue(resultado.isBloqueado());
        assertEquals("YA_FUERA", resultado.getMotivo());
        assertFalse(ticket.isDentro());
        verify(ticketDao, never()).save(any());
        verifyNoInteractions(accesoDao, api);
    }

    @Test
    void movimientoEsObligatorio() {
        assertThrows(com.uap.control_tickets.exception.NegocioException.class,
                () -> service.validar("qr", null));
        verifyNoInteractions(ticketDao, accesoDao, api);
    }

    @Test
    void listadoIncluyePersonaYUltimaEntradaSinConsultarApi() {
        Ticket ticket = ticket(CategoriaTicket.DOCENTE, true);
        var entrada = mock(AccesoDao.UltimaEntrada.class);
        Instant hora = Instant.parse("2026-09-15T20:00:00Z");
        when(entrada.getIdTicket()).thenReturn(1L);
        when(entrada.getEntrada()).thenReturn(hora);
        when(ticketDao.findAllByDentroTrueAndEstado(EstadoRegistro.ACTIVO)).thenReturn(List.of(ticket));
        when(accesoDao.ultimasEntradasDentro(EstadoRegistro.ACTIVO)).thenReturn(List.of(entrada));
        var persona = service.personasDentro().getFirst();
        assertEquals(9L, persona.getIdPersona());
        assertEquals(hora, persona.getEntrada());
        assertEquals("DOCENTE", persona.getCategoria());
        verify(accesoDao, never()).findTopByTicketIdTicketOrderByFechaHoraDesc(any());
        verifyNoInteractions(api);
    }
}
