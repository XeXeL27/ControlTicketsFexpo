package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.persona.PersonaDetalleDto;
import com.uap.control_tickets.dto.persona.PersonaDto;

import java.util.List;

/** Contrato del CRUD de Persona. */
public interface PersonaService {
    List<PersonaDetalleDto> listar();
    PersonaDetalleDto obtener(Long idPersona);
    PersonaDetalleDto crear(PersonaDto dto);
    PersonaDetalleDto actualizar(Long idPersona, PersonaDto dto);
    void eliminar(Long idPersona);
}
