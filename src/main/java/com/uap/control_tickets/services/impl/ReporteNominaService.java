package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.reporte.NominaDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.repository.AdministrativoDao;
import com.uap.control_tickets.models.repository.DocenteDao;
import com.uap.control_tickets.models.repository.TicketDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReporteNominaService {

    private final AdministrativoDao administrativoDao;
    private final DocenteDao docenteDao;
    private final TicketDao ticketDao;

    @Transactional(readOnly = true)
    public NominaDto nomina() {
        List<NominaDto.FilaNomina> filas = new ArrayList<>();

        // Administrativos activos (los convertidos a docente quedan ELIMINADOS y no aparecen aquí)
        for (var adm : administrativoDao.findAllByEstado(EstadoRegistro.ACTIVO)) {
            var persona = adm.getPersona();
            var fila = new NominaDto.FilaNomina();
            fila.setCodigo(adm.getCodigoAdministrativo());
            fila.setNombreCompleto(persona.getNombreCompleto());
            fila.setCi(persona.getCi());
            fila.setCategoria("ADMINISTRATIVO");
            fila.setCategoriaEtiqueta("Administrativo");

            var ticketOpt = ticketDao.findFirstByAdministrativoIdAdministrativoAndEstado(
                    adm.getIdAdministrativo(), EstadoRegistro.ACTIVO);
            if (ticketOpt.isPresent()) {
                var t = ticketOpt.get();
                fila.setEntregado(t.isEntregado());
                fila.setRechazado(t.isRechazado());
                fila.setFechaEntrega(t.getFechaEntrega());
                fila.setFechaRechazo(t.getFechaRechazo());
                fila.setCodigoTicket(t.getCodigoIdentificacion());
            } else {
                fila.setEntregado(false);
                fila.setRechazado(false);
                fila.setFechaEntrega(null);
                fila.setFechaRechazo(null);
                fila.setCodigoTicket(null);
            }
            filas.add(fila);
        }

        // Docentes activos (incluye promovidos desde administrativo)
        for (var doc : docenteDao.findAllByEstado(EstadoRegistro.ACTIVO)) {
            var persona = doc.getPersona();
            var fila = new NominaDto.FilaNomina();
            fila.setCodigo(doc.getCodigoDocente());
            fila.setNombreCompleto(persona.getNombreCompleto());
            fila.setCi(persona.getCi());
            fila.setCategoria("DOCENTE");
            fila.setCategoriaEtiqueta("Docente");

            var ticketOpt = ticketDao.findFirstByDocenteIdDocenteAndEstado(
                    doc.getIdDocente(), EstadoRegistro.ACTIVO);
            if (ticketOpt.isPresent()) {
                var t = ticketOpt.get();
                fila.setEntregado(t.isEntregado());
                fila.setRechazado(t.isRechazado());
                fila.setFechaEntrega(t.getFechaEntrega());
                fila.setFechaRechazo(t.getFechaRechazo());
                fila.setCodigoTicket(t.getCodigoIdentificacion());
            } else {
                fila.setEntregado(false);
                fila.setRechazado(false);
                fila.setFechaEntrega(null);
                fila.setFechaRechazo(null);
                fila.setCodigoTicket(null);
            }
            filas.add(fila);
        }

        // Orden alfabético por nombre (estable para PDF)
        filas.sort(Comparator.comparing(
                NominaDto.FilaNomina::getNombreCompleto, String.CASE_INSENSITIVE_ORDER));

        long entregados = filas.stream().filter(NominaDto.FilaNomina::isEntregado).count();
        long rechazados = filas.stream().filter(NominaDto.FilaNomina::isRechazado).count();
        long totalAdministrativos = filas.stream().filter(f -> "ADMINISTRATIVO".equals(f.getCategoria())).count();
        long totalDocentes = filas.stream().filter(f -> "DOCENTE".equals(f.getCategoria())).count();

        NominaDto dto = new NominaDto();
        dto.setGeneradoEn(Instant.now());
        dto.setFilas(filas);
        dto.setTotal(filas.size());
        dto.setEntregados(entregados);
        dto.setRechazados(rechazados);
        // pendientes = ni entregados ni rechazados
        dto.setPendientes(filas.size() - entregados - rechazados);
        dto.setTotalAdministrativos(totalAdministrativos);
        dto.setTotalDocentes(totalDocentes);
        return dto;
    }
}
