package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.enums.EstadoHuellaDetalle;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Estudiante;
import com.uap.control_tickets.models.entity.HuellaDigital;
import com.uap.control_tickets.models.entity.SincronizacionHuella;
import com.uap.control_tickets.models.entity.SincronizacionHuellaDetalle;
import com.uap.control_tickets.models.repository.EstudianteDao;
import com.uap.control_tickets.models.repository.HuellaDigitalDao;
import com.uap.control_tickets.models.repository.SincronizacionHuellaDao;
import com.uap.control_tickets.models.repository.SincronizacionHuellaDetalleDao;
import com.uap.control_tickets.services.biometrico.UsuarioBiometrico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Procesa UN usuario del biométrico dentro de su propia transacción.
 *
 * Componente aparte (y no un método privado del service async) a propósito:
 * cada usuario se guarda en una transacción chica e independiente, igual que
 * la emisión masiva de tickets — si un RU falla, no tumba el lote entero.
 */
@Component
@RequiredArgsConstructor
public class HuellaUsuarioProcesador {

    private final EstudianteDao estudianteDao;
    private final HuellaDigitalDao huellaDao;
    private final SincronizacionHuellaDao jobDao;
    private final SincronizacionHuellaDetalleDao detalleDao;

    /** Lo que salió con un usuario, para sumar al contador del job. */
    public record ResultadoParcial(EstadoHuellaDetalle estado, String mensaje) {
    }

    /**
     * Cruza el PIN (= RU) con el sistema, guarda/actualiza los templates y
     * deja la fila del reporte. {@code rusVistos} trae los RUs ya procesados
     * en ESTE job (vive en el hilo async, no en BD).
     */
    @Transactional
    public ResultadoParcial procesar(Long jobId, String equipo, UsuarioBiometrico u, Set<String> rusVistos) {
        SincronizacionHuella job = jobDao.findById(jobId).orElseThrow();
        String ru = u.getPin() == null ? "" : u.getPin().trim();

        Estudiante est = estudianteDao.findByRu(ru).orElse(null);
        if (est == null || est.getEstado() != EstadoRegistro.ACTIVO) {
            guardarDetalle(job, equipo, ru, EstadoHuellaDetalle.NO_ENCONTRADO,
                    "El PIN " + ru + " no es ningún RU registrado en el sistema");
            return new ResultadoParcial(EstadoHuellaDetalle.NO_ENCONTRADO, null);
        }
        if (u.getTemplates().isEmpty()) {
            guardarDetalle(job, equipo, ru, EstadoHuellaDetalle.SIN_HUELLA,
                    "El RU existe (" + est.getPersona().getNombreCompleto() + ") pero no tiene huella enrolada en " + equipo);
            rusVistos.add(ru);
            return new ResultadoParcial(EstadoHuellaDetalle.SIN_HUELLA, null);
        }

        boolean repetidoEnJob = !rusVistos.add(ru); // add devuelve false si ya estaba
        String templateRepetidoDe = null;
        int guardados = 0;
        for (Map.Entry<Integer, String> e : u.getTemplates().entrySet()) {
            Integer dedo = e.getKey();
            String tpl = e.getValue();
            // ¿Este mismo template ya está a nombre de OTRO estudiante?
            if (templateRepetidoDe == null) {
                templateRepetidoDe = otroDuenoDelTemplate(tpl, est.getIdEstudiante());
            }
            var previo = huellaDao.findByEstudianteIdEstudianteAndDedoAndEstado(
                    est.getIdEstudiante(), dedo, EstadoRegistro.ACTIVO).orElse(null);
            if (previo != null && tpl.equals(previo.getTemplate())) continue; // idéntico, nada que hacer
            HuellaDigital h = previo != null ? previo : new HuellaDigital();
            h.setEstado(EstadoRegistro.ACTIVO);
            h.setEstudiante(est);
            h.setDedo(dedo);
            h.setTemplate(tpl);
            h.setVersionBiometrica(u.getVersionBiometrica());
            h.setEquipoOrigen(equipo);
            h.setFechaCaptura(Instant.now());
            huellaDao.save(h);
            guardados++;
        }

        est.setTieneHuella(true);
        est.setFechaHuella(Instant.now());
        estudianteDao.save(est);

        EstadoHuellaDetalle estado;
        String mensaje;
        // Qué dedos trae (nº de slot del equipo): ej. "0, 1".
        String dedos = u.getTemplates().keySet().stream().sorted()
                .map(String::valueOf).collect(Collectors.joining(", "));
        if (repetidoEnJob) {
            estado = EstadoHuellaDetalle.DUPLICADO;
            mensaje = "RU repetido en más de un equipo — dedos " + dedos + " (se guardó una vez)";
        } else if (templateRepetidoDe != null) {
            estado = EstadoHuellaDetalle.DUPLICADO;
            mensaje = "Dedos " + dedos + " + template idéntico al del RU "
                    + templateRepetidoDe + " (posible doble enrolamiento)";
        } else {
            estado = EstadoHuellaDetalle.CORRECTO;
            mensaje = guardados == 0 ? "Ya tenía los dedos " + dedos + " guardados"
                    : "Dedos guardados: " + dedos;
        }
        guardarDetalle(job, equipo, ru, estado, mensaje);
        return new ResultadoParcial(estado, mensaje);
    }

    /** Avanza los contadores del job (transacción propia, chica). */
    @Transactional
    public void avanzar(Long jobId, int sumaTotal, EstadoHuellaDetalle estado) {
        SincronizacionHuella job = jobDao.findById(jobId).orElseThrow();
        job.setTotalUsuarios(job.getTotalUsuarios() + sumaTotal);
        if (estado != null) {
            job.setProcesados(job.getProcesados() + 1);
            switch (estado) {
                case CORRECTO -> job.setCorrectos(job.getCorrectos() + 1);
                case DUPLICADO -> job.setDuplicados(job.getDuplicados() + 1);
                case NO_ENCONTRADO -> job.setNoEncontrados(job.getNoEncontrados() + 1);
                case SIN_HUELLA -> job.setSinHuella(job.getSinHuella() + 1);
                case ERROR -> job.setErrores(job.getErrores() + 1);
            }
        }
        jobDao.save(job);
    }

    /** Marca el job en su estado final (transacción propia, chica). */
    @Transactional
    public void cerrar(Long jobId, com.uap.control_tickets.enums.EstadoSincronizacion estado, String mensajeError) {
        SincronizacionHuella job = jobDao.findById(jobId).orElseThrow();
        job.setEstadoJob(estado);
        job.setFechaFin(Instant.now());
        if (mensajeError != null && !mensajeError.isBlank()) {
            String previo = job.getMensajeError() == null ? "" : job.getMensajeError() + " | ";
            job.setMensajeError((previo + mensajeError).length() > 490
                    ? (previo + mensajeError).substring(0, 490) : previo + mensajeError);
        }
        jobDao.save(job);
    }

    // -------------------------------------------------------------------------

    private void guardarDetalle(SincronizacionHuella job, String equipo, String ru,
                                EstadoHuellaDetalle estado, String mensaje) {
        SincronizacionHuellaDetalle d = new SincronizacionHuellaDetalle();
        d.setEstado(EstadoRegistro.ACTIVO);
        d.setSincronizacion(job);
        d.setRu(ru);
        d.setEquipo(equipo);
        d.setResultado(estado);
        d.setMensaje(mensaje);
        detalleDao.save(d);
    }

    /** RU de otro estudiante que ya tenga este mismo template, o null. */
    private String otroDuenoDelTemplate(String template, Long idEstudiantePropio) {
        return huellaDao.findAllByTemplateAndEstado(template, EstadoRegistro.ACTIVO).stream()
                .filter(h -> !h.getEstudiante().getIdEstudiante().equals(idEstudiantePropio))
                .map(h -> h.getEstudiante().getRu())
                .findFirst().orElse(null);
    }
}
