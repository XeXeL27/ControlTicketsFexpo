package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.persona.PersonaDetalleDto;
import com.uap.control_tickets.dto.persona.PersonaDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.TipoPersona;
import com.uap.control_tickets.models.repository.AdministrativoDao;
import com.uap.control_tickets.models.repository.DocenteDao;
import com.uap.control_tickets.models.repository.EstudianteDao;
import com.uap.control_tickets.models.repository.UsuarioDao;
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
import java.util.Set;

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
    private final EstudianteDao estudianteDao;
    private final AdministrativoDao administrativoDao;
    private final DocenteDao docenteDao;
    private final UsuarioDao usuarioDao;

    @Override
    @Transactional(readOnly = true)
    public List<PersonaDetalleDto> listar() {
        // Se clasifica con 4 consultas de ids y no una por persona: con miles de
        // registros, preguntar de a uno haria la pantalla inusable.
        Set<Long> estudiantes = Set.copyOf(estudianteDao.idsPersonaActivas(EstadoRegistro.ACTIVO));
        Set<Long> administrativos = Set.copyOf(administrativoDao.idsPersonaActivas(EstadoRegistro.ACTIVO));
        Set<Long> docentes = Set.copyOf(docenteDao.idsPersonaActivas(EstadoRegistro.ACTIVO));
        Set<Long> conUsuario = Set.copyOf(usuarioDao.idsPersonaConUsuario(EstadoRegistro.ACTIVO));

        return personaDao.findAllByEstado(EstadoRegistro.ACTIVO)
                .stream()
                .map(p -> {
                    PersonaDetalleDto dto = toDetalleDto(p);
                    dto.setTipo(clasificar(p.getIdPersona(), estudiantes, administrativos,
                            docentes, conUsuario).name());
                    return dto;
                })
                .toList();
    }

    /**
     * Primer vinculo que aplique, en orden de interes.
     * Una persona puede ser docente Y tener usuario; se muestra como DOCENTE.
     */
    private TipoPersona clasificar(Long id, Set<Long> estudiantes, Set<Long> administrativos,
                                   Set<Long> docentes, Set<Long> conUsuario) {
        if (estudiantes.contains(id)) return TipoPersona.ESTUDIANTE;
        if (administrativos.contains(id)) return TipoPersona.ADMINISTRATIVO;
        if (docentes.contains(id)) return TipoPersona.DOCENTE;
        if (conUsuario.contains(id)) return TipoPersona.USUARIO;
        return TipoPersona.SIN_VINCULO;
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
