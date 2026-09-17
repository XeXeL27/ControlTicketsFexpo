package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Administrativo;
import com.uap.control_tickets.models.entity.Docente;
import com.uap.control_tickets.models.repository.AdministrativoDao;
import com.uap.control_tickets.models.repository.DocenteDao;
import com.uap.control_tickets.models.repository.TicketDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CambioTipoService {
    private final AdministrativoDao administrativoDao;
    private final DocenteDao docenteDao;
    private final TicketDao ticketDao;

    @Transactional
    public void aDocente(Long id, String carrera) {
        var origen = administrativoDao.buscarParaCambio(id)
                .filter(a -> a.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Administrativo no encontrado o ya convertido"));
        Long persona = origen.getPersona().getIdPersona();
        if (docenteDao.findByPersonaIdPersonaAndEstado(persona, EstadoRegistro.ACTIVO).isPresent()) {
            throw new NegocioException("La persona ya tiene un registro docente activo");
        }
        var destino = docenteDao.findByCodigoDocente(origen.getCodigoAdministrativo()).orElseGet(Docente::new);
        if (destino.getIdDocente() != null && !destino.getPersona().getIdPersona().equals(persona)) {
            throw new NegocioException("El codigo ya pertenece a otro docente");
        }
        String nuevaCarrera = carrera == null ? "" : carrera.trim();
        if (nuevaCarrera.isEmpty()) nuevaCarrera = destino.getCarrera();
        if (nuevaCarrera == null || nuevaCarrera.isBlank() || nuevaCarrera.length() > 255) {
            throw new NegocioException("Indique la carrera del docente (maximo 255 caracteres)");
        }
        destino.setPersona(origen.getPersona());
        destino.setCodigoDocente(origen.getCodigoAdministrativo());
        destino.setCarrera(nuevaCarrera);
        destino.setEstado(EstadoRegistro.ACTIVO);
        docenteDao.save(destino);
        // Se mantienen las mismas filas: QR, codigo, accesos, impresion y entrega.
        for (var ticket : ticketDao.findAllByAdministrativoIdAdministrativo(id)) {
            ticket.setCategoria(CategoriaTicket.DOCENTE);
            ticket.setAdministrativo(null);
            ticket.setDocente(destino);
        }
        origen.setEstado(EstadoRegistro.ELIMINADO);
    }

    @Transactional
    public void aAdministrativo(Long id) {
        var origen = docenteDao.buscarParaCambio(id)
                .filter(d -> d.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Docente no encontrado o ya convertido"));
        Long persona = origen.getPersona().getIdPersona();
        if (administrativoDao.findByPersonaIdPersonaAndEstado(persona, EstadoRegistro.ACTIVO).isPresent()) {
            throw new NegocioException("La persona ya tiene un registro administrativo activo");
        }
        var destino = administrativoDao.findByCodigoAdministrativo(origen.getCodigoDocente())
                .orElseGet(Administrativo::new);
        if (destino.getIdAdministrativo() != null && !destino.getPersona().getIdPersona().equals(persona)) {
            throw new NegocioException("El codigo ya pertenece a otro administrativo");
        }
        destino.setPersona(origen.getPersona());
        destino.setCodigoAdministrativo(origen.getCodigoDocente());
        destino.setEstado(EstadoRegistro.ACTIVO);
        administrativoDao.save(destino);
        for (var ticket : ticketDao.findAllByDocenteIdDocente(id)) {
            ticket.setCategoria(CategoriaTicket.ADMINISTRATIVO);
            ticket.setDocente(null);
            ticket.setAdministrativo(destino);
        }
        // La carrera se conserva en el registro docente para una futura vuelta.
        origen.setEstado(EstadoRegistro.ELIMINADO);
    }
}
