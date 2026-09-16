package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.docente.DocenteDetalleDto;
import com.uap.control_tickets.dto.docente.DocenteDto;
import com.uap.control_tickets.dto.docente.PrevisualizacionDocCsvDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Docente;
import com.uap.control_tickets.models.entity.Persona;
import com.uap.control_tickets.models.repository.DocenteDao;
import com.uap.control_tickets.models.repository.PersonaDao;
import com.uap.control_tickets.models.repository.TicketDao;
import com.uap.control_tickets.services.interfaces.DocenteService;
import com.uap.control_tickets.Utils.csv.CsvUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Set;

/**
 * CRUD de Docente + importación masiva por CSV.
 * CSV por POSICIÓN fija: codigo docente, nombre completo, ci.
 * Calcado de {@code AdministrativoServiceImpl}: misma detección de codificación
 * (vía {@link CsvUtils}), previsualización y reimport que ACTUALIZA (reconoce por
 * el código docente) en vez de fallar.
 */
@Service
@RequiredArgsConstructor
public class DocenteServiceImpl implements DocenteService {

    private final DocenteDao docenteDao;
    private final PersonaDao personaDao;
    private final TicketDao ticketDao;

    /** Rótulos habituales en la primera celda del CSV, ya normalizados. */
    private static final Set<String> PALABRAS_ENCABEZADO =
            Set.of("codigo", "codigodocente", "docente", "item", "nro", "n", "codigodoc");

    /** Cuantas filas se muestran en la vista previa (el resto solo se cuenta). */
    private static final int FILAS_PREVIA = 15;

    @Override
    @Transactional(readOnly = true)
    public List<DocenteDetalleDto> listar() {
        return docenteDao.findAllByEstado(EstadoRegistro.ACTIVO)
                .stream().map(this::toDetalleDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocenteDetalleDto obtener(Long id) {
        return toDetalleDto(buscarActivo(id));
    }

    @Override
    @Transactional
    public DocenteDetalleDto crear(DocenteDto dto) {
        return toDetalleDto(crearDocente(dto, false));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Docente d = buscarActivo(id);
        d.setEstado(EstadoRegistro.ELIMINADO);
        docenteDao.save(d);
    }

    // -------------------------------------------------------------------------
    // Importación CSV
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public ImportacionResultadoDto importarCsv(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("El archivo CSV está vacío");
        }
        ImportacionResultadoDto resultado = new ImportacionResultadoDto();

        String contenido;
        try {
            contenido = CsvUtils.decodificar(archivo.getBytes());
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }

        try (BufferedReader br = new BufferedReader(new StringReader(contenido))) {
            // Orden FIJO: codigo docente, nombre completo, ci.
            String linea;
            int fila = 0;
            char sep = ',';
            boolean primera = true;
            while ((linea = br.readLine()) != null) {
                if (primera) {
                    linea = CsvUtils.quitarBom(linea);
                    sep = CsvUtils.detectarSeparador(linea);
                    primera = false;
                    if (esEncabezado(linea, sep)) continue;
                }
                if (linea.isBlank()) continue;
                fila++;
                resultado.setTotalFilas(resultado.getTotalFilas() + 1);
                try {
                    DocenteDto dto = filaADto(linea, sep);
                    // Si el código ya estaba, la fila actualiza en vez de crear.
                    boolean yaExistia = docenteDao.findByCodigoDocente(
                            dto.getCodigoDocente() == null ? "" : dto.getCodigoDocente().trim()
                    ).isPresent();
                    crearDocente(dto, true);
                    if (yaExistia) {
                        resultado.setActualizados(resultado.getActualizados() + 1);
                    } else {
                        resultado.setCreados(resultado.getCreados() + 1);
                    }
                } catch (NegocioException ex) {
                    resultado.agregarError(fila, ex.getMessage());
                } catch (Exception ex) {
                    resultado.agregarError(fila, "Error inesperado: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }
        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public PrevisualizacionDocCsvDto previsualizarCsv(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("El archivo CSV está vacío");
        }
        String[] decodificado;
        try {
            decodificado = CsvUtils.decodificarConNombre(archivo.getBytes());
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }

        PrevisualizacionDocCsvDto p = new PrevisualizacionDocCsvDto();
        p.setCodificacion(decodificado[0]);

        try (BufferedReader br = new BufferedReader(new StringReader(decodificado[1]))) {
            String linea;
            int fila = 0;
            char sep = ',';
            boolean primera = true;
            while ((linea = br.readLine()) != null) {
                if (primera) {
                    linea = CsvUtils.quitarBom(linea);
                    sep = CsvUtils.detectarSeparador(linea);
                    p.setSeparador(String.valueOf(sep));
                    primera = false;
                    if (esEncabezado(linea, sep)) {
                        p.setEncabezadoDetectado(true);
                        p.setEncabezado(linea);
                        continue;
                    }
                }
                if (linea.isBlank()) continue;
                fila++;
                p.setTotalFilas(fila);

                DocenteDto dto = filaADto(linea, sep);
                PrevisualizacionDocCsvDto.FilaPrevia fp = new PrevisualizacionDocCsvDto.FilaPrevia();
                fp.setFila(fila);
                fp.setCodigoDocente(dto.getCodigoDocente());
                fp.setNombreCompleto(dto.getNombreCompleto());
                fp.setCi(dto.getCi());

                if (dto.getCodigoDocente() == null || dto.getCi() == null) {
                    fp.setEstado(dto.getCodigoDocente() == null ? "Falta el código" : "Falta el CI");
                    p.setConProblemas(p.getConProblemas() + 1);
                } else if (docenteDao.findByCodigoDocente(dto.getCodigoDocente()).isPresent()) {
                    fp.setEstado("ACTUALIZA");
                    p.setExistentes(p.getExistentes() + 1);
                } else {
                    fp.setEstado("NUEVO");
                    p.setNuevos(p.getNuevos() + 1);
                }

                String sospechoso = CsvUtils.textoSospechoso(dto.getNombreCompleto());
                if (sospechoso != null) fp.setAdvertencia(sospechoso);

                if (p.getFilas().size() < FILAS_PREVIA) p.getFilas().add(fp);
            }
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }
        return p;
    }

    // -------------------------------------------------------------------------
    // Alta común (usada por crear() y por la importación)
    // -------------------------------------------------------------------------

    private Docente crearDocente(DocenteDto dto, boolean actualizar) {
        String ci = req(dto.getCi(), "CI");
        String codigo = req(dto.getCodigoDocente(), "código docente");

        Docente previo = docenteDao.findByCodigoDocente(codigo).orElse(null);
        if (previo != null && previo.getEstado() == EstadoRegistro.ACTIVO && !actualizar) {
            throw new NegocioException("Ya existe un docente con el código '" + codigo + "'");
        }

        Persona borrador = personaDao.findByCi(ci).orElseGet(Persona::new);
        aplicarNombre(borrador, dto);
        borrador.setCi(ci);
        borrador.setEstado(EstadoRegistro.ACTIVO);
        Persona persona = personaDao.save(borrador);

        boolean ocupada = docenteDao
                .findByPersonaIdPersonaAndEstado(persona.getIdPersona(), EstadoRegistro.ACTIVO)
                .filter(d -> previo == null || !d.getIdDocente().equals(previo.getIdDocente()))
                .isPresent();
        if (ocupada) {
            throw new NegocioException("La persona con CI '" + ci + "' ya está registrada como docente");
        }

        Docente d = previo != null ? previo : new Docente();
        d.setEstado(EstadoRegistro.ACTIVO);
        d.setCodigoDocente(codigo);
        d.setPersona(persona);
        return docenteDao.save(d);
    }

    private void aplicarNombre(Persona p, DocenteDto dto) {
        if (dto.getNombreCompleto() != null && !dto.getNombreCompleto().isBlank()) {
            p.setNombre(dto.getNombreCompleto().trim());
            p.setPaterno("");   // columna NOT NULL; el nombre completo va en 'nombre'
            p.setMaterno(null);
        } else {
            p.setNombre(req(dto.getNombre(), "nombre").trim());
            p.setPaterno(req(dto.getPaterno(), "paterno").trim());
            p.setMaterno(dto.getMaterno() != null && !dto.getMaterno().isBlank()
                    ? dto.getMaterno().trim() : null);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers de parseo CSV (los genéricos viven en CsvUtils)
    // -------------------------------------------------------------------------

    /** Encabezado si la 1ª celda es un rótulo conocido o el CI (columna 2) no tiene dígitos. */
    private boolean esEncabezado(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        if (c.length == 0) return false;

        String primera = CsvUtils.normalizar(c[0]);
        if (PALABRAS_ENCABEZADO.contains(primera)) return true;

        String ci = CsvUtils.get(c, 2);
        if (ci == null) return false;
        return ci.chars().noneMatch(Character::isDigit);
    }

    /** Posicional: [0]=codigo docente, [1]=nombre completo, [2]=ci. */
    private DocenteDto filaADto(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        DocenteDto dto = new DocenteDto();
        dto.setCodigoDocente(CsvUtils.get(c, 0));
        dto.setNombreCompleto(CsvUtils.get(c, 1));
        dto.setCi(CsvUtils.get(c, 2));
        return dto;
    }

    // -------------------------------------------------------------------------
    // Helpers varios
    // -------------------------------------------------------------------------

    private String req(String v, String campo) {
        if (v == null || v.isBlank()) throw new NegocioException("El campo '" + campo + "' es obligatorio");
        return v.trim();
    }

    private Docente buscarActivo(Long id) {
        return docenteDao.findById(id)
                .filter(d -> d.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Docente no encontrado"));
    }

    private DocenteDetalleDto toDetalleDto(Docente d) {
        DocenteDetalleDto dto = new DocenteDetalleDto();
        dto.setIdDocente(d.getIdDocente());
        dto.setCodigoDocente(d.getCodigoDocente());
        dto.setEstado(d.getEstado().name());
        Persona p = d.getPersona();
        dto.setIdPersona(p.getIdPersona());
        dto.setNombreCompleto(p.getNombreCompleto());
        dto.setCi(p.getCi());
        ticketDao.findFirstByDocenteIdDocenteAndEstado(d.getIdDocente(), EstadoRegistro.ACTIVO)
                .ifPresent(t -> {
                    dto.setIdTicket(t.getIdTicket());
                    dto.setCodigoTicket(t.getCodigoIdentificacion());
                });
        return dto;
    }
}
