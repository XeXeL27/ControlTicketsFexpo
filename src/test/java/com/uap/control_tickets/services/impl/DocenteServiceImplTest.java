package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Docente;
import com.uap.control_tickets.models.entity.Persona;
import com.uap.control_tickets.models.repository.DocenteDao;
import com.uap.control_tickets.models.repository.PersonaDao;
import com.uap.control_tickets.models.repository.TicketDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocenteServiceImplTest {
    @Mock DocenteDao docenteDao;
    @Mock PersonaDao personaDao;
    @Mock TicketDao ticketDao;
    @InjectMocks DocenteServiceImpl service;

    private MockMultipartFile csv(String contenido) {
        return new MockMultipartFile("archivo", "docentes.csv", "text/csv",
                contenido.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void previaDetectaEncabezadoCarreraYDatosObligatoriosSinGuardar() {
        var previa = service.previsualizarCsv(csv("código docente;nombre completo;ci;carrera\n"
                + "42;María Pérez;123456;Ingeniería\n43;Juan López;654321;\n"));
        assertTrue(previa.isEncabezadoDetectado());
        assertEquals(2, previa.getTotalFilas());
        assertEquals(1, previa.getNuevos());
        assertEquals(1, previa.getConProblemas());
        assertEquals("Ingeniería", previa.getFilas().getFirst().getCarrera());
        assertEquals("Falta la carrera", previa.getFilas().get(1).getEstado());
        verify(docenteDao, never()).save(any());
        verifyNoInteractions(personaDao);
    }

    @Test
    void importaCuatroColumnasYOmiteFilaInvalida() {
        when(personaDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var resultado = service.importarCsv(csv("42,María Pérez,123456,Ingeniería\n"
                + "43,Juan López,654321,\n"));
        assertEquals(1, resultado.getCreados());
        assertEquals(1, resultado.getErrores().size());
        var captor = ArgumentCaptor.forClass(Docente.class);
        verify(docenteDao).save(captor.capture());
        assertEquals("42", captor.getValue().getCodigoDocente());
        assertEquals("Ingeniería", captor.getValue().getCarrera());
        assertEquals("María Pérez", captor.getValue().getPersona().getNombreCompleto());
        assertEquals("123456", captor.getValue().getPersona().getCi());
        verify(personaDao, times(1)).save(any());
    }

    @Test
    void reimportarActualizaCarreraSinDuplicarDocente() {
        Persona persona = new Persona();
        persona.setIdPersona(1L);
        persona.setCi("123456");
        Docente docente = new Docente();
        docente.setIdDocente(2L);
        docente.setCodigoDocente("42");
        docente.setCarrera("Anterior");
        docente.setPersona(persona);
        when(docenteDao.findByCodigoDocente("42")).thenReturn(Optional.of(docente));
        when(personaDao.findByCi("123456")).thenReturn(Optional.of(persona));
        when(personaDao.save(persona)).thenReturn(persona);
        when(docenteDao.findByPersonaIdPersonaAndEstado(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(docente));
        var resultado = service.importarCsv(csv("42,María Pérez,123456,Medicina\n"));
        assertEquals(1, resultado.getActualizados());
        assertEquals(0, resultado.getCreados());
        assertTrue(resultado.getErrores().isEmpty());
        verify(docenteDao).save(docente);
        assertEquals("Medicina", docente.getCarrera());
    }
}
