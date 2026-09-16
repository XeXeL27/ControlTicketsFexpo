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
 * CSV por POSICIÓN fija: codigo docente, nombre completo, ci, carrera.
 * El nombre completo (un solo campo) se guarda en Persona.nombre con paterno="".
 *
 * Comparte con estudiantes toda la parte delicada del CSV (detección de codificación,
 * separador, encabezado) vía {@link CsvUtils}, y sigue el mismo patrón: reimportar el
 * padrón ACTUALIZA a los que ya existían (se reconocen por el código docente)
 * en vez de fallar.
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
        Docente a = buscarActivo(id);
        a.setEstado(EstadoRegistro.ELIMINADO);
        docenteDao.save(a);
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
            // Orden FIJO: codigo docente, nombre completo, ci, carrera.
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
                    String problema = problemaFila(dto);
                    if (problema != null) throw new NegocioException(problema);
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

    /**
     * Lee el CSV y cuenta que pasaria, SIN escribir nada en la base.
     * Usa el mismo camino que importarCsv() para que la vista previa refleje lo que
     * realmente se va a guardar.
     */
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
                fp.setCarrera(dto.getCarrera());

                String problema = problemaFila(dto);
                if (problema != null) {
                    fp.setEstado(problema);
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

    /**
     * Alta común del docente.
     *
     * @param actualizar si es true (importación CSV) y el código ya existe activo, se
     *                   ACTUALIZAN sus datos con los del archivo en vez de fallar.
     *                   En el alta individual va false: ahí un código repetido es un error.
     */
    private Docente crearDocente(DocenteDto dto, boolean actualizar) {
        String ci = req(dto.getCi(), "CI");
        String codigo = req(dto.getCodigoDocente(), "código docente");
        String carrera = req(dto.getCarrera(), "carrera");
        if (codigo.length() > 30 || ci.length() > 20 || carrera.length() > 255) {
            throw new NegocioException("Código, CI o carrera excede su longitud máxima (30, 20 y 255)");
        }
        Persona nombreValidado = new Persona();
        aplicarNombre(nombreValidado, dto);
        if (nombreValidado.getNombre().length() > 100) {
            throw new NegocioException("El nombre no puede superar 100 caracteres");
        }

        // El código identifica al docente: si ya existe esa fila se reutiliza
        //  - si estaba ELIMINADA, se revive (el UNIQUE de la base impide insertar otra);
        //  - si estaba ACTIVA y venimos de un CSV, se ACTUALIZA con los datos del archivo.
        Docente previo = docenteDao.findByCodigoDocente(codigo).orElse(null);
        if (previo != null && previo.getEstado() == EstadoRegistro.ACTIVO && !actualizar) {
            throw new NegocioException("Ya existe un docente con el código '" + codigo + "'");
        }

        // Persona: se reutiliza por CI (reescribiendo el nombre con el del archivo) o se crea.
        Persona borrador = personaDao.findByCi(ci).orElseGet(Persona::new);

        // La misma persona no puede figurar dos veces como docente activo
        // (se excluye la fila que estamos reviviendo, que es de esta persona).
        boolean ocupada = borrador.getIdPersona() != null && docenteDao
                .findByPersonaIdPersonaAndEstado(borrador.getIdPersona(), EstadoRegistro.ACTIVO)
                .filter(a -> previo == null || !a.getIdDocente().equals(previo.getIdDocente()))
                .isPresent();
        if (ocupada) {
            throw new NegocioException("La persona con CI '" + ci + "' ya está registrada como docente");
        }

        // Mantener la identidad de un docente con ticket emitido al reimportar.
        if (previo != null && !previo.getPersona().getCi().equals(ci)
                && ticketDao.findFirstByDocenteIdDocenteAndEstado(
                        previo.getIdDocente(), EstadoRegistro.ACTIVO).isPresent()) {
            throw new NegocioException("No se puede cambiar el CI de un docente con ticket emitido");
        }
        aplicarNombre(borrador, dto);
        borrador.setCi(ci);
        borrador.setEstado(EstadoRegistro.ACTIVO);
        Persona persona = personaDao.save(borrador);

        Docente a = previo != null ? previo : new Docente();
        a.setEstado(EstadoRegistro.ACTIVO);
        a.setCodigoDocente(codigo);
        a.setCarrera(carrera);
        a.setPersona(persona);
        return docenteDao.save(a);
    }

    /**
     * Copia el nombre del DTO a la Persona.
     * Del CSV llega el nombre completo en un solo campo; del alta individual llegan
     * nombre/paterno/materno por separado.
     */
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

    /**
     * Detecta si la primera línea es un encabezado y no un docente.
     * El código docente puede no ser numérico, así que la regla fiable es que
     * **el CI de un dato siempre trae dígitos**: si la celda del CI no tiene ninguno,
     * la fila es un rótulo. Se acepta además cualquier variante conocida en la 1ª celda.
     */
    private boolean esEncabezado(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        if (c.length == 0) return false;

        String primera = CsvUtils.normalizar(c[0]);
        if (PALABRAS_ENCABEZADO.contains(primera)) return true;

        // Un CI real (columna 2) trae dígitos; un rótulo ("CI", "C.I.") no.
        String ci = CsvUtils.get(c, 2);
        if (ci == null) return false;
        return ci.chars().noneMatch(Character::isDigit);
    }

    /** Posicional: [0]=codigo docente, [1]=nombre completo, [2]=ci, [3]=carrera. */
    private DocenteDto filaADto(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        DocenteDto dto = new DocenteDto();
        dto.setCodigoDocente(CsvUtils.get(c, 0));
        dto.setNombreCompleto(CsvUtils.get(c, 1));
        dto.setCi(CsvUtils.get(c, 2));
        dto.setCarrera(CsvUtils.get(c, 3));
        return dto;
    }

    private String problemaFila(DocenteDto dto) {
        if (dto.getCodigoDocente() == null) return "Falta el código docente";
        if (dto.getNombreCompleto() == null) return "Falta el nombre completo";
        if (dto.getCi() == null) return "Falta el CI";
        if (dto.getCarrera() == null) return "Falta la carrera";
        if (dto.getCodigoDocente().length() > 30 || dto.getNombreCompleto().length() > 100
                || dto.getCi().length() > 20 || dto.getCarrera().length() > 255) {
            return "Algún campo excede su longitud máxima";
        }
        return null;
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
                .filter(a -> a.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Docente no encontrado"));
    }

    private DocenteDetalleDto toDetalleDto(Docente a) {
        DocenteDetalleDto dto = new DocenteDetalleDto();
        dto.setIdDocente(a.getIdDocente());
        dto.setCodigoDocente(a.getCodigoDocente());
        dto.setCarrera(a.getCarrera());
        dto.setEstado(a.getEstado().name());
        Persona p = a.getPersona();
        dto.setIdPersona(p.getIdPersona());
        dto.setNombreCompleto(p.getNombreCompleto());
        dto.setCi(p.getCi());
        ticketDao.findFirstByDocenteIdDocenteAndEstado(a.getIdDocente(), EstadoRegistro.ACTIVO)
                .ifPresent(t -> {
                    dto.setIdTicket(t.getIdTicket());
                    dto.setCodigoTicket(t.getCodigoIdentificacion());
                });
        return dto;
    }
}
