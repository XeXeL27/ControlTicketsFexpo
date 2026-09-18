package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.huella.CargaMasivaDto;
import com.uap.control_tickets.dto.huella.HuellaDigitalDto;
import com.uap.control_tickets.dto.huella.ProgresoHuellaDto;
import com.uap.control_tickets.dto.huella.ResultadoHuellaDto;
import com.uap.control_tickets.enums.DireccionSincronizacion;
import com.uap.control_tickets.enums.EstadoHuellaDetalle;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.EstadoSincronizacion;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.DispositivoBiometrico;
import com.uap.control_tickets.models.entity.SincronizacionHuella;
import com.uap.control_tickets.models.repository.DispositivoBiometricoDao;
import com.uap.control_tickets.models.repository.EstudianteDao;
import com.uap.control_tickets.models.repository.HuellaDigitalDao;
import com.uap.control_tickets.models.repository.SincronizacionHuellaDao;
import com.uap.control_tickets.models.repository.SincronizacionHuellaDetalleDao;
import com.uap.control_tickets.services.biometrico.BiometriaException;
import com.uap.control_tickets.services.biometrico.BiometricoDriver;
import com.uap.control_tickets.services.biometrico.BiometricoDriverSelector;
import com.uap.control_tickets.services.biometrico.CancelacionSincronizacion;
import com.uap.control_tickets.services.biometrico.DedoBiometrico;
import com.uap.control_tickets.services.biometrico.LineaCarga;
import com.uap.control_tickets.services.biometrico.ProgresoBiometria;
import com.uap.control_tickets.services.biometrico.UsuarioBiometrico;
import com.uap.control_tickets.services.biometrico.UsuarioCarga;
import com.uap.control_tickets.services.interfaces.SincronizacionHuellaService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Sincronización de huellas con los biométricos (tarea larga en 2º plano).
 *
 * {@link #iniciar} crea el job y vuelve enseguida; {@link #ejecutar} corre en
 * el pool "huellas" (un job por vez) recorriendo equipo por equipo. Por cada
 * usuario avisa al progreso (WS + contadores en BD para el polling) y delega
 * el guardado a {@link HuellaUsuarioProcesador} (transacción chica por RU).
 */
@Service
@RequiredArgsConstructor
public class SincronizacionHuellaServiceImpl implements SincronizacionHuellaService {

    private final SincronizacionHuellaDao jobDao;
    private final SincronizacionHuellaDetalleDao detalleDao;
    private final DispositivoBiometricoDao dispositivoDao;
    private final EstudianteDao estudianteDao;
    private final HuellaDigitalDao huellaDao;
    private final HuellaUsuarioProcesador procesador;
    private final BiometricoDriverSelector selector;
    private final SimpMessagingTemplate ws;

    /** Cancelaciones pedidas desde la pantalla (jobId → true). */
    private final ConcurrentHashMap<Long, AtomicBoolean> cancelados = new ConcurrentHashMap<>();
    /** RUs ya vistos por job (para el DUPLICADO entre equipos). Vive en el hilo async. */
    private final ConcurrentHashMap<Long, Set<String>> rusPorJob = new ConcurrentHashMap<>();

    private BiometricoDriver driver() {
        return selector.actual();
    }

    // -------------------------------------------------------------------------
    // API sincrónica (la pantalla)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public Long iniciar(List<Long> idsEquipos) {
        List<DispositivoBiometrico> equipos = equiposPara(idsEquipos);
        if (equipos.isEmpty()) {
            throw new NegocioException("No hay biométricos activos para sincronizar");
        }
        SincronizacionHuella job = new SincronizacionHuella();
        job.setEstado(EstadoRegistro.ACTIVO);
        job.setEstadoJob(EstadoSincronizacion.EN_CURSO);
        job.setEquipos(equipos.stream().map(DispositivoBiometrico::getNombre).collect(Collectors.joining(", ")));
        job = jobDao.save(job);
        cancelados.put(job.getIdSincronizacion(), new AtomicBoolean(false));
        ejecutar(job.getIdSincronizacion(),
                equipos.stream().map(DispositivoBiometrico::getIdDispositivo).toList());
        return job.getIdSincronizacion();
    }

    @Override
    @Transactional(readOnly = true)
    public ProgresoHuellaDto progreso(Long jobId) {
        return toProgreso(buscar(jobId), null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoHuellaDto resultado(Long jobId, String filtroEstado) {
        SincronizacionHuella job = buscar(jobId);
        ResultadoHuellaDto out = new ResultadoHuellaDto();
        out.setJobId(job.getIdSincronizacion());
        out.setEstado(job.getEstadoJob().name());
        out.setDireccion(job.getDireccion().name());
        out.setEquipos(job.getEquipos());
        out.setTotal(job.getTotalUsuarios());
        out.setCorrectos(job.getCorrectos());
        out.setDuplicados(job.getDuplicados());
        out.setNoEncontrados(job.getNoEncontrados());
        out.setSinHuella(job.getSinHuella());
        out.setErrores(job.getErrores());
        out.setMensajeError(job.getMensajeError());

        String filtro = filtroEstado == null ? "TODOS" : filtroEstado.trim().toUpperCase();
        out.setFiltroEstado(filtro);
        var detalles = "TODOS".equals(filtro)
                ? detalleDao.findAllBySincronizacionIdSincronizacionAndEstadoOrderByIdDetalle(jobId, EstadoRegistro.ACTIVO)
                : detalleDao.findAllBySincronizacionIdSincronizacionAndEstadoAndResultadoOrderByIdDetalle(
                        jobId, EstadoHuellaDetalle.valueOf(filtro), EstadoRegistro.ACTIVO);
        for (var d : detalles) {
            ResultadoHuellaDto.DetalleHuellaDto f = new ResultadoHuellaDto.DetalleHuellaDto();
            f.setRu(d.getRu());
            f.setEquipo(d.getEquipo());
            f.setEstado(d.getResultado().name());
            f.setMensaje(d.getMensaje());
            out.getDetalles().add(f);
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgresoHuellaDto> historial() {
        return jobDao.findAllByEstadoOrderByIdSincronizacionDesc(EstadoRegistro.ACTIVO)
                .stream().map(j -> toProgreso(j, null, null)).toList();
    }

    @Override
    public void cancelar(Long jobId) {
        SincronizacionHuella job = buscar(jobId);
        if (job.getEstadoJob() != EstadoSincronizacion.EN_CURSO) {
            throw new NegocioException("La sincronización ya terminó (" + job.getEstadoJob() + ")");
        }
        cancelados.computeIfAbsent(jobId, k -> new AtomicBoolean(false)).set(true);
    }

    @Override
    @Transactional
    public Long iniciarCarga(CargaMasivaDto dto) {
        DispositivoBiometrico eq = dispositivoDao.findById(dto.getIdDispositivo())
                .filter(e -> e.getEstado() == EstadoRegistro.ACTIVO && e.getActivo())
                .orElseThrow(() -> new NegocioException("Elegí un biométrico activo"));
        String campo = dto.getCampo() == null ? "" : dto.getCampo().trim().toUpperCase();
        String valor = dto.getValor() == null ? "" : dto.getValor().trim();
        if (!"FACULTAD".equals(campo) && !"CARRERA".equals(campo)) {
            throw new NegocioException("El campo debe ser FACULTAD o CARRERA");
        }
        if (valor.isEmpty()) throw new NegocioException("Elegí la facultad/carrera a cargar");
        // Solo suben los que tienen huella: los demás se ignoran por completo.
        long cuantos = "FACULTAD".equals(campo)
                ? estudianteDao.contarConHuellaPorFacultad(EstadoRegistro.ACTIVO, valor)
                : estudianteDao.contarConHuellaPorCarrera(EstadoRegistro.ACTIVO, valor);
        if (cuantos == 0) {
            throw new NegocioException("Nadie de " + valor + " tiene huella: no hay nada que cargar");
        }
        SincronizacionHuella job = new SincronizacionHuella();
        job.setEstado(EstadoRegistro.ACTIVO);
        job.setEstadoJob(EstadoSincronizacion.EN_CURSO);
        job.setDireccion(DireccionSincronizacion.SUBIDA);
        job.setEquipos("Carga " + campo.toLowerCase() + " " + valor + " → " + eq.getNombre()
                + " (" + eq.getIp() + ")");
        job.setTotalUsuarios((int) cuantos);
        job = jobDao.save(job);
        cancelados.put(job.getIdSincronizacion(), new AtomicBoolean(false));
        ejecutarCarga(job.getIdSincronizacion(), eq.getIdDispositivo(), campo, valor);
        return job.getIdSincronizacion();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HuellaDigitalDto> huellasDeEstudiante(Long idEstudiante) {
        var est = estudianteDao.findById(idEstudiante)
                .filter(e -> e.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante no encontrado"));
        return huellaDao.findAllByEstudianteIdEstudianteAndEstado(
                        est.getIdEstudiante(), EstadoRegistro.ACTIVO).stream()
                .sorted((a, b) -> a.getDedo().compareTo(b.getDedo()))
                .map(h -> {
                    HuellaDigitalDto dto = new HuellaDigitalDto();
                    dto.setIdHuella(h.getIdHuella());
                    dto.setDedo(h.getDedo());
                    dto.setNombreDedo(DedoBiometrico.etiqueta(h.getDedo()));
                    dto.setEquipoOrigen(h.getEquipoOrigen());
                    dto.setVersionBiometrica(h.getVersionBiometrica());
                    dto.setFechaCaptura(h.getFechaCaptura() == null ? null : h.getFechaCaptura().toString());
                    dto.setTamanoBytes(h.getTemplate() == null ? 0 : h.getTemplate().length() * 3 / 4);
                    return dto;
                }).toList();
    }

    // -------------------------------------------------------------------------
    // Tarea async
    // -------------------------------------------------------------------------

    /** Corre en el pool "huellas". NO lleva @Transactional: cada paso abre la suya. */
    @Async("huellasExecutor")
    public void ejecutar(Long jobId, List<Long> idsEquipos) {
        Set<String> rusVistos = ConcurrentHashMap.newKeySet();
        rusPorJob.put(jobId, rusVistos);
        List<String> fallos = new ArrayList<>();
        try {
            for (Long idEq : idsEquipos) {
                exigirNoCancelado(jobId);
                DispositivoBiometrico eq = dispositivoDao.findById(idEq).orElse(null);
                if (eq == null || eq.getEstado() != EstadoRegistro.ACTIVO || !eq.getActivo()) continue;
                String equipo = eq.getNombre() + " (" + eq.getIp() + ")";
                try {
                    driver().leerUsuarios(eq, new ProgresoBiometria() {
                        @Override
                        public void listaDescubierta(String equipoCb, int totalEquipo) {
                            procesador.avanzar(jobId, totalEquipo, null);
                            publicar(jobId, null, equipoCb);
                        }

                        @Override
                        public void usuarioListo(String equipoCb, UsuarioBiometrico u,
                                                 int procesadosEquipo, int totalEquipo) {
                            exigirNoCancelado(jobId);
                            var parcial = procesador.procesar(jobId, equipoCb, u, rusVistos);
                            procesador.avanzar(jobId, 0, parcial.estado());
                            publicar(jobId, u.getPin(), equipoCb);
                        }
                    });
                } catch (CancelacionSincronizacion c) {
                    throw c;
                } catch (BiometriaException e) {
                    // Un equipo caído no frena a los demás: se anota y se sigue.
                    fallos.add(equipo + ": " + e.getMessage());
                    publicar(jobId, null, equipo);
                } catch (RuntimeException e) {
                    fallos.add(equipo + ": error inesperado (" + e.getMessage() + ")");
                    publicar(jobId, null, equipo);
                }
            }
            exigirNoCancelado(jobId);
            SincronizacionHuella job = buscar(jobId);
            if (!fallos.isEmpty() && job.getTotalUsuarios() == 0 && job.getProcesados() == 0) {
                procesador.cerrar(jobId, EstadoSincronizacion.ERROR, String.join(" | ", fallos));
            } else {
                procesador.cerrar(jobId, EstadoSincronizacion.FINALIZADO,
                        fallos.isEmpty() ? null : "Equipos con falla: " + String.join(" | ", fallos));
            }
            publicar(jobId, null, null);
        } catch (CancelacionSincronizacion c) {
            procesador.cerrar(jobId, EstadoSincronizacion.CANCELADO, "Cancelada por el usuario");
            publicar(jobId, null, null);
        } catch (RuntimeException e) {
            try {
                procesador.cerrar(jobId, EstadoSincronizacion.ERROR, "Error inesperado: " + e.getMessage());
                publicar(jobId, null, null);
            } catch (RuntimeException ignored) {
            }
        } finally {
            rusPorJob.remove(jobId);
        }
    }

    /**
     * Carga masiva sistema → equipo. Corre en el pool "huellas" (un job por
     * vez), SIN @Transactional: cada línea abre la suya en el procesador.
     * El script avisa un RU por vez y acá se anota + publica en vivo.
     */
    @Async("huellasExecutor")
    public void ejecutarCarga(Long jobId, Long idDispositivo, String campo, String valor) {
        try {
            exigirNoCancelado(jobId);
            DispositivoBiometrico eq = dispositivoDao.findById(idDispositivo).orElse(null);
            if (eq == null || eq.getEstado() != EstadoRegistro.ACTIVO || !eq.getActivo()) {
                throw new NegocioException("El biométrico ya no está activo");
            }
            String equipo = eq.getNombre() + " (" + eq.getIp() + ")";
            List<UsuarioCarga> carga = procesador.datosParaCarga(
                    ("FACULTAD".equals(campo)
                            ? estudianteDao.findAllByEstadoAndFacultad(EstadoRegistro.ACTIVO, valor)
                            : estudianteDao.findAllByEstadoAndCarrera(EstadoRegistro.ACTIVO, valor))
                            .stream().map(e -> e.getIdEstudiante()).toList())
                    // Sin huella no sube: se ignora por completo.
                    .stream().filter(u -> !u.templates().isEmpty()).toList();
            driver().cargarUsuarios(eq, carga, linea -> {
                exigirNoCancelado(jobId);
                EstadoHuellaDetalle estado;
                try {
                    estado = EstadoHuellaDetalle.valueOf(linea.estado());
                } catch (IllegalArgumentException e) {
                    estado = EstadoHuellaDetalle.ERROR;
                }
                procesador.anotar(jobId, equipo, linea.ru(), estado, linea.mensaje());
                // En SUBIDA "correctos" = cargados + actualizados.
                procesador.avanzar(jobId, 0,
                        estado == EstadoHuellaDetalle.ERROR ? EstadoHuellaDetalle.ERROR
                                : EstadoHuellaDetalle.CORRECTO);
                publicar(jobId, linea.ru(), equipo);
            });
            exigirNoCancelado(jobId);
            procesador.cerrar(jobId, EstadoSincronizacion.FINALIZADO, null);
            publicar(jobId, null, null);
        } catch (CancelacionSincronizacion c) {
            procesador.cerrar(jobId, EstadoSincronizacion.CANCELADO, "Cancelada por el usuario");
            publicar(jobId, null, null);
        } catch (BiometriaException e) {
            procesador.cerrar(jobId, EstadoSincronizacion.ERROR, e.getMessage());
            publicar(jobId, null, null);
        } catch (RuntimeException e) {
            try {
                procesador.cerrar(jobId, EstadoSincronizacion.ERROR, "Error inesperado: " + e.getMessage());
                publicar(jobId, null, null);
            } catch (RuntimeException ignored) {
            }
        }
    }

    // -------------------------------------------------------------------------

    private void exigirNoCancelado(Long jobId) {        AtomicBoolean flag = cancelados.get(jobId);
        if (flag != null && flag.get()) throw new CancelacionSincronizacion();
    }

    /** Publica el progreso en /topic/huellas/{jobId} (la barra en vivo). */
    private void publicar(Long jobId, String ruActual, String equipoActual) {
        try {
            SincronizacionHuella job = buscar(jobId);
            ws.convertAndSend("/topic/huellas/" + jobId, toProgreso(job, ruActual, equipoActual));
        } catch (RuntimeException ignored) {
            // Sin WS igual queda el polling por BD.
        }
    }

    private List<DispositivoBiometrico> equiposPara(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            return dispositivoDao.findAllById(ids).stream()
                    .filter(e -> e.getEstado() == EstadoRegistro.ACTIVO && e.getActivo())
                    .toList();
        }
        return dispositivoDao.findAllByEstadoAndActivoTrue(EstadoRegistro.ACTIVO);
    }

    private SincronizacionHuella buscar(Long jobId) {
        return jobDao.findById(jobId)
                .filter(j -> j.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sincronización no encontrada"));
    }

    private ProgresoHuellaDto toProgreso(SincronizacionHuella j, String ruActual, String equipoActual) {
        ProgresoHuellaDto p = new ProgresoHuellaDto();
        p.setJobId(j.getIdSincronizacion());
        p.setEstado(j.getEstadoJob().name());
        p.setDireccion(j.getDireccion().name());
        p.setEquipos(j.getEquipos());
        p.setTotal(j.getTotalUsuarios());
        p.setProcesados(j.getProcesados());
        p.setPorcentaje(j.getTotalUsuarios() <= 0 ? 0
                : Math.min(100, (int) Math.round(j.getProcesados() * 100.0 / j.getTotalUsuarios())));
        p.setRuActual(ruActual);
        p.setEquipoActual(equipoActual);
        p.setCorrectos(j.getCorrectos());
        p.setDuplicados(j.getDuplicados());
        p.setNoEncontrados(j.getNoEncontrados());
        p.setSinHuella(j.getSinHuella());
        p.setErrores(j.getErrores());
        p.setMensajeError(j.getMensajeError());
        return p;
    }
}
