package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.persona.PersonaDetalleDto;
import com.uap.control_tickets.dto.persona.PersonaDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.Genero;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Persona;
import com.uap.control_tickets.models.repository.PersonaDao;
import com.uap.control_tickets.services.interfaces.PersonaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD de Persona.
 *
 * Patron general de cada metodo:
 *  - listar/obtener: solo lectura (@Transactional(readOnly = true)).
 *  - crear/actualizar: validan reglas de negocio (CI/correo unicos) antes de guardar.
 *  - eliminar: borrado LOGICO (estado = ELIMINADO), nunca DELETE fisico.
 * Se convierte entre entidad y DTO a mano (toDetalleDto) para controlar
 * exactamente que datos salen al cliente.
 */
@Service
@RequiredArgsConstructor
public class PersonaServiceImpl implements PersonaService {

    private final PersonaDao personaDao;

    @Override
    @Transactional(readOnly = true)
    public List<PersonaDetalleDto> listar() {
        return personaDao.findAllByEstado(EstadoRegistro.ACTIVO)
                .stream()
                .map(this::toDetalleDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PersonaDetalleDto obtener(Long idPersona) {
        return toDetalleDto(buscarActiva(idPersona));
    }

    @Override
    @Transactional
    public PersonaDetalleDto crear(PersonaDto dto) {
        if (personaDao.existsByCi(dto.getCi().trim())) {
            throw new NegocioException("Ya existe una persona con el CI '" + dto.getCi() + "'");
        }
        Persona persona = new Persona();
        aplicarDatos(persona, dto);
        return toDetalleDto(personaDao.save(persona));
    }

    @Override
    @Transactional
    public PersonaDetalleDto actualizar(Long idPersona, PersonaDto dto) {
        Persona persona = buscarActiva(idPersona);

        if (personaDao.existsByCiAndIdPersonaNot(dto.getCi().trim(), idPersona)) {
            throw new NegocioException("Ya existe otra persona con el CI '" + dto.getCi() + "'");
        }

        aplicarDatos(persona, dto);
        return toDetalleDto(personaDao.save(persona));
    }

    @Override
    @Transactional
    public void eliminar(Long idPersona) {
        Persona persona = buscarActiva(idPersona);
        persona.setEstado(EstadoRegistro.ELIMINADO);
        personaDao.save(persona);
    }

    // ---------------- helpers ----------------

    /** Copia los datos del DTO a la entidad, normalizando y convirtiendo el genero. */
    private void aplicarDatos(Persona persona, PersonaDto dto) {
        persona.setNombre(dto.getNombre().trim());
        persona.setPaterno(dto.getPaterno().trim());
        persona.setMaterno(dto.getMaterno() != null ? dto.getMaterno().trim() : null);
        persona.setCi(dto.getCi().trim());
        if (dto.getGenero() != null && !dto.getGenero().isBlank()) {
            try {
                persona.setGenero(Genero.valueOf(dto.getGenero().trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                persona.setGenero(null);
            }
        } else {
            persona.setGenero(null);
        }
    }

    private Persona buscarActiva(Long idPersona) {
        return personaDao.findById(idPersona)
                .filter(p -> p.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Persona no encontrada"));
    }

    private PersonaDetalleDto toDetalleDto(Persona p) {
        PersonaDetalleDto dto = new PersonaDetalleDto();
        dto.setIdPersona(p.getIdPersona());
        dto.setNombre(p.getNombre());
        dto.setPaterno(p.getPaterno());
        dto.setMaterno(p.getMaterno());
        dto.setNombreCompleto(p.getNombreCompleto());
        dto.setCi(p.getCi());
        dto.setGenero(p.getGenero() != null ? p.getGenero().name() : null);
        dto.setEstado(p.getEstado().name());
        return dto;
    }
}
