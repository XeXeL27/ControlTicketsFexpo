package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.estudiante.EstudianteDetalleDto;
import com.uap.control_tickets.dto.estudiante.EstudianteDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.dto.estudiante.PrevisualizacionCsvDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Estudiante;
import com.uap.control_tickets.models.entity.Persona;
import com.uap.control_tickets.models.entity.Ticket;
import com.uap.control_tickets.models.repository.EstudianteDao;
import com.uap.control_tickets.models.repository.PersonaDao;
import com.uap.control_tickets.models.repository.TicketDao;
import com.uap.control_tickets.services.interfaces.EstudianteService;
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
 * CRUD de Estudiante + importación masiva por CSV.
 *
 * En el alta (individual o por CSV) se crea/reutiliza la Persona por su CI y se
 * registra el Estudiante con su RU, facultad y carrera.
 */
@Service
@RequiredArgsConstructor
public class EstudianteServiceImpl implements EstudianteService {

    private final EstudianteDao estudianteDao;
    private final PersonaDao personaDao;
    private final TicketDao ticketDao;

    /** Rótulos habituales en la primera celda del CSV, ya normalizados. */
    private static final Set<String> PALABRAS_ENCABEZADO =
            Set.of("ru", "nru", "nroru", "numeroru", "codigo", "registro");

    @Override
    @Transactional(readOnly = true)
    public List<EstudianteDetalleDto> listar() {
        return estudianteDao.findAllByEstado(EstadoRegistro.ACTIVO)
                .stream().map(this::toDetalleDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EstudianteDetalleDto obtener(Long idEstudiante) {
        return toDetalleDto(buscarActivo(idEstudiante));
    }

    @Override
    @Transactional
    public EstudianteDetalleDto crear(EstudianteDto dto) {
        return toDetalleDto(crearEstudiante(dto, false));
    }

    @Override
    @Transactional
    public void eliminar(Long idEstudiante) {
        Estudiante e = buscarActivo(idEstudiante);
        e.setEstado(EstadoRegistro.ELIMINADO);
        estudianteDao.save(e);
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

            // Orden FIJO de columnas del CSV de estudiantes: ru, nombre completo, ci, carrera.
            String linea;
            int fila = 0;
            char sep = ',';
            boolean primera = true;
            while ((linea = br.readLine()) != null) {
                if (primera) {
                    linea = CsvUtils.quitarBom(linea);
                    sep = CsvUtils.detectarSeparador(linea);
                    primera = false;
                    if (esEncabezado(linea, sep)) continue; // salta la fila de encabezado si existe
                }
                if (linea.isBlank()) continue;
                fila++;
                resultado.setTotalFilas(resultado.getTotalFilas() + 1);
                try {
                    EstudianteDto dto = filaADto(linea, sep);
                    // Si el RU ya estaba, la fila actualiza en vez de crear.
                    boolean yaExistia = estudianteDao.findByRu(dto.getRu() == null
                            ? "" : dto.getRu().trim()).isPresent();
                    crearEstudiante(dto, true);
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

    /** Cuantas filas se muestran en la vista previa (el resto solo se cuenta). */
    private static final int FILAS_PREVIA = 15;

    /**
     * Lee el CSV y cuenta que pasaria, SIN escribir nada en la base.
     * Usa exactamente el mismo camino que importarCsv(), para que lo que se ve en
     * pantalla sea lo que realmente se va a guardar.
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> facultades() {
        return estudianteDao.facultadesDistintas(EstadoRegistro.ACTIVO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> carreras() {
        return estudianteDao.carrerasDistintas(EstadoRegistro.ACTIVO);
    }

    @Override
    @Transactional(readOnly = true)
    public PrevisualizacionCsvDto previsualizarCsv(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("El archivo CSV está vacío");
        }
        String[] decodificado;
        try {
            decodificado = CsvUtils.decodificarConNombre(archivo.getBytes());
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }

        PrevisualizacionCsvDto p = new PrevisualizacionCsvDto();
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

                EstudianteDto dto = filaADto(linea, sep);
                PrevisualizacionCsvDto.FilaPrevia fp = new PrevisualizacionCsvDto.FilaPrevia();
                fp.setFila(fila);
                fp.setRu(dto.getRu());
                fp.setNombreCompleto(dto.getNombreCompleto());
                fp.setCi(dto.getCi());
                fp.setCarrera(dto.getCarrera());

                if (dto.getRu() == null || dto.getCi() == null) {
                    fp.setEstado(dto.getRu() == null ? "Falta el R.U." : "Falta el CI");
                    p.setConProblemas(p.getConProblemas() + 1);
                } else if (estudianteDao.findByRu(dto.getRu()).isPresent()) {
                    fp.setEstado("ACTUALIZA");
                    p.setExistentes(p.getExistentes() + 1);
                } else {
                    fp.setEstado("NUEVO");
                    p.setNuevos(p.getNuevos() + 1);
                }

                String sospechoso = CsvUtils.textoSospechoso(dto.getNombreCompleto(), dto.getCarrera());
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

    /**
     * Alta comun del estudiante.
     *
     * @param actualizar si es true (importacion CSV) y el RU ya existe activo, se
     *                   ACTUALIZAN sus datos con los del archivo en vez de fallar.
     *                   En el alta individual va false: ahi un RU repetido es un error.
     */
    private Estudiante crearEstudiante(EstudianteDto dto, boolean actualizar) {
        String ci = req(dto.getCi(), "CI");
        String ru = req(dto.getRu(), "RU");

        // El RU identifica al estudiante, asi que si ya existe esa fila se reutiliza:
        //  - si estaba ELIMINADA, se revive (el UNIQUE de la base impide insertar otra);
        //  - si estaba ACTIVA y venimos de un CSV, se ACTUALIZA con los datos del archivo.
        // Esto hace que volver a subir el padron corrija los datos ya cargados.
        // En el alta individual (actualizar=false) un RU repetido sigue siendo un error,
        // para no pisar un registro sin querer desde el formulario.
        Estudiante previo = estudianteDao.findByRu(ru).orElse(null);
        if (previo != null && previo.getEstado() == EstadoRegistro.ACTIVO && !actualizar) {
            throw new NegocioException("Ya existe un estudiante con el RU '" + ru + "'");
        }

        // Persona: se reutiliza si ya existe por CI, si no se crea.
        // Al reutilizarla se REESCRIBE el nombre con el del archivo: si no, un
        // reimport hecho para corregir los datos (p. ej. tildes mal leidas) dejaria
        // intacto el nombre viejo, porque la Persona se busca por CI y no cambia.
        Persona borrador = personaDao.findByCi(ci).orElseGet(Persona::new);
        aplicarNombre(borrador, dto);
        borrador.setCi(ci);
        borrador.setEstado(EstadoRegistro.ACTIVO); // vuelve a la vida si estaba eliminada
        Persona persona = personaDao.save(borrador);

        // La misma persona no puede figurar dos veces como estudiante activo.
        // Se excluye la fila que estamos reviviendo, que obviamente es de esta persona.
        boolean ocupada = estudianteDao
                .findByPersonaIdPersonaAndEstado(persona.getIdPersona(), EstadoRegistro.ACTIVO)
                .filter(e -> previo == null || !e.getIdEstudiante().equals(previo.getIdEstudiante()))
                .isPresent();
        if (ocupada) {
            throw new NegocioException("La persona con CI '" + ci + "' ya está registrada como estudiante");
        }

        Estudiante estudiante = previo != null ? previo : new Estudiante();
        estudiante.setEstado(EstadoRegistro.ACTIVO);
        estudiante.setRu(ru);
        estudiante.setFacultad(vacioNull(dto.getFacultad()));
        estudiante.setCarrera(vacioNull(dto.getCarrera()));
        estudiante.setPersona(persona);
        return estudianteDao.save(estudiante);
    }

    /**
     * Copia el nombre del DTO a la Persona.
     * Del CSV llega el nombre completo en un solo campo; del alta individual llegan
     * nombre/paterno/materno por separado.
     */
    private void aplicarNombre(Persona p, EstudianteDto dto) {
        if (dto.getNombreCompleto() != null && !dto.getNombreCompleto().isBlank()) {
            p.setNombre(dto.getNombreCompleto().trim());
            p.setPaterno("");   // columna NOT NULL; el nombre completo va en 'nombre'
            p.setMaterno(null);
        } else {
            p.setNombre(req(dto.getNombre(), "nombre").trim());
            p.setPaterno(req(dto.getPaterno(), "paterno").trim());
            p.setMaterno(vacioNull(dto.getMaterno()));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers de parseo CSV (los genéricos viven en CsvUtils)
    // -------------------------------------------------------------------------

    /**
     * Detecta si la primera línea es un encabezado y no un estudiante.
     *
     * No alcanza con comparar contra "ru": en los CSV reales la celda viene como
     * "R.U.", "RU:", " Ru " o con tildes. La regla que de verdad separa un
     * encabezado de un dato es que **el RU de un estudiante siempre es numérico**;
     * si la primera celda no tiene ningún dígito, es un rótulo.
     * Se acepta además cualquier variante que normalizada sea una palabra conocida.
     */
    private boolean esEncabezado(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        if (c.length == 0) return false;

        String primera = CsvUtils.normalizar(c[0]);
        if (primera.isEmpty()) return false;
        if (PALABRAS_ENCABEZADO.contains(primera)) return true;

        // Un RU real trae dígitos; un rótulo ("R.U.", "CODIGO", "Nº") no.
        return primera.chars().noneMatch(Character::isDigit);
    }

    /** Mapea una fila del CSV por POSICIÓN: [0]=ru, [1]=nombre completo, [2]=ci, [3]=carrera. */
    private EstudianteDto filaADto(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        EstudianteDto dto = new EstudianteDto();
        dto.setRu(CsvUtils.get(c, 0));
        dto.setNombreCompleto(CsvUtils.get(c, 1));
        dto.setCi(CsvUtils.get(c, 2));
        dto.setCarrera(CsvUtils.get(c, 3));
        return dto;
    }

    // -------------------------------------------------------------------------
    // Helpers varios
    // -------------------------------------------------------------------------

    private String req(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new NegocioException("El campo '" + campo + "' es obligatorio");
        }
        return valor.trim();
    }

    private String vacioNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private Estudiante buscarActivo(Long idEstudiante) {
        return estudianteDao.findById(idEstudiante)
                .filter(e -> e.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante no encontrado"));
    }

    private EstudianteDetalleDto toDetalleDto(Estudiante e) {
        EstudianteDetalleDto dto = new EstudianteDetalleDto();
        dto.setIdEstudiante(e.getIdEstudiante());
        dto.setRu(e.getRu());
        dto.setFacultad(e.getFacultad());
        dto.setCarrera(e.getCarrera());
        dto.setEstado(e.getEstado().name());
        dto.setTieneHuella(Boolean.TRUE.equals(e.getTieneHuella()));
        dto.setFechaHuella(e.getFechaHuella() == null ? null : e.getFechaHuella().toString());

        Persona p = e.getPersona();
        dto.setIdPersona(p.getIdPersona());
        dto.setNombreCompleto(p.getNombreCompleto());
        dto.setCi(p.getCi());

        // Si ya tiene ticket emitido, informarlo.
        ticketDao.findFirstByEstudianteIdEstudianteAndEstado(e.getIdEstudiante(), EstadoRegistro.ACTIVO)
                .ifPresent(t -> {
                    dto.setIdTicket(t.getIdTicket());
                    dto.setCodigoTicket(t.getCodigoIdentificacion());
                });
        return dto;
    }
}
