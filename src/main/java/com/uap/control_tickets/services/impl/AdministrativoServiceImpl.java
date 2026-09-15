package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.administrativo.AdministrativoDetalleDto;
import com.uap.control_tickets.dto.administrativo.AdministrativoDto;
import com.uap.control_tickets.dto.administrativo.PrevisualizacionAdmCsvDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Administrativo;
import com.uap.control_tickets.models.entity.Persona;
import com.uap.control_tickets.models.repository.AdministrativoDao;
import com.uap.control_tickets.models.repository.PersonaDao;
import com.uap.control_tickets.models.repository.TicketDao;
import com.uap.control_tickets.services.interfaces.AdministrativoService;
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
 * CRUD de Administrativo + importación masiva por CSV.
 * CSV por POSICIÓN fija: codigo administrativo, nombre completo, ci.
 * El nombre completo (un solo campo) se guarda en Persona.nombre con paterno="".
 *
 * Comparte con estudiantes toda la parte delicada del CSV (detección de codificación,
 * separador, encabezado) vía {@link CsvUtils}, y sigue el mismo patrón: reimportar el
 * padrón ACTUALIZA a los que ya existían (se reconocen por el código administrativo)
 * en vez de fallar.
 */
@Service
@RequiredArgsConstructor
public class AdministrativoServiceImpl implements AdministrativoService {

    private final AdministrativoDao administrativoDao;
    private final PersonaDao personaDao;
    private final TicketDao ticketDao;

    /** Rótulos habituales en la primera celda del CSV, ya normalizados. */
    private static final Set<String> PALABRAS_ENCABEZADO =
            Set.of("codigo", "codigoadministrativo", "administrativo", "item", "nro", "n", "codigoadm");

    /** Cuantas filas se muestran en la vista previa (el resto solo se cuenta). */
    private static final int FILAS_PREVIA = 15;

    @Override
    @Transactional(readOnly = true)
    public List<AdministrativoDetalleDto> listar() {
        return administrativoDao.findAllByEstado(EstadoRegistro.ACTIVO)
                .stream().map(this::toDetalleDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdministrativoDetalleDto obtener(Long id) {
        return toDetalleDto(buscarActivo(id));
    }

    @Override
    @Transactional
    public AdministrativoDetalleDto crear(AdministrativoDto dto) {
        return toDetalleDto(crearAdministrativo(dto, false));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Administrativo a = buscarActivo(id);
        a.setEstado(EstadoRegistro.ELIMINADO);
        administrativoDao.save(a);
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
            // Orden FIJO: codigo administrativo, nombre completo, ci.
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
                    AdministrativoDto dto = filaADto(linea, sep);
                    // Si el código ya estaba, la fila actualiza en vez de crear.
                    boolean yaExistia = administrativoDao.findByCodigoAdministrativo(
                            dto.getCodigoAdministrativo() == null ? "" : dto.getCodigoAdministrativo().trim()
                    ).isPresent();
                    crearAdministrativo(dto, true);
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
    public PrevisualizacionAdmCsvDto previsualizarCsv(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("El archivo CSV está vacío");
        }
        String[] decodificado;
        try {
            decodificado = CsvUtils.decodificarConNombre(archivo.getBytes());
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }

        PrevisualizacionAdmCsvDto p = new PrevisualizacionAdmCsvDto();
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

                AdministrativoDto dto = filaADto(linea, sep);
                PrevisualizacionAdmCsvDto.FilaPrevia fp = new PrevisualizacionAdmCsvDto.FilaPrevia();
                fp.setFila(fila);
                fp.setCodigoAdministrativo(dto.getCodigoAdministrativo());
                fp.setNombreCompleto(dto.getNombreCompleto());
                fp.setCi(dto.getCi());

                if (dto.getCodigoAdministrativo() == null || dto.getCi() == null) {
                    fp.setEstado(dto.getCodigoAdministrativo() == null ? "Falta el código" : "Falta el CI");
                    p.setConProblemas(p.getConProblemas() + 1);
                } else if (administrativoDao.findByCodigoAdministrativo(dto.getCodigoAdministrativo()).isPresent()) {
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
     * Alta común del administrativo.
     *
     * @param actualizar si es true (importación CSV) y el código ya existe activo, se
     *                   ACTUALIZAN sus datos con los del archivo en vez de fallar.
     *                   En el alta individual va false: ahí un código repetido es un error.
     */
    private Administrativo crearAdministrativo(AdministrativoDto dto, boolean actualizar) {
        String ci = req(dto.getCi(), "CI");
        String codigo = req(dto.getCodigoAdministrativo(), "código administrativo");

        // El código identifica al administrativo: si ya existe esa fila se reutiliza
        //  - si estaba ELIMINADA, se revive (el UNIQUE de la base impide insertar otra);
        //  - si estaba ACTIVA y venimos de un CSV, se ACTUALIZA con los datos del archivo.
        Administrativo previo = administrativoDao.findByCodigoAdministrativo(codigo).orElse(null);
        if (previo != null && previo.getEstado() == EstadoRegistro.ACTIVO && !actualizar) {
            throw new NegocioException("Ya existe un administrativo con el código '" + codigo + "'");
        }

        // Persona: se reutiliza por CI (reescribiendo el nombre con el del archivo) o se crea.
        Persona borrador = personaDao.findByCi(ci).orElseGet(Persona::new);
        aplicarNombre(borrador, dto);
        borrador.setCi(ci);
        borrador.setEstado(EstadoRegistro.ACTIVO);
        Persona persona = personaDao.save(borrador);

        // La misma persona no puede figurar dos veces como administrativo activo
        // (se excluye la fila que estamos reviviendo, que es de esta persona).
        boolean ocupada = administrativoDao
                .findByPersonaIdPersonaAndEstado(persona.getIdPersona(), EstadoRegistro.ACTIVO)
                .filter(a -> previo == null || !a.getIdAdministrativo().equals(previo.getIdAdministrativo()))
                .isPresent();
        if (ocupada) {
            throw new NegocioException("La persona con CI '" + ci + "' ya está registrada como administrativo");
        }

        Administrativo a = previo != null ? previo : new Administrativo();
        a.setEstado(EstadoRegistro.ACTIVO);
        a.setCodigoAdministrativo(codigo);
        a.setPersona(persona);
        return administrativoDao.save(a);
    }

    /**
     * Copia el nombre del DTO a la Persona.
     * Del CSV llega el nombre completo en un solo campo; del alta individual llegan
     * nombre/paterno/materno por separado.
     */
    private void aplicarNombre(Persona p, AdministrativoDto dto) {
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
     * Detecta si la primera línea es un encabezado y no un administrativo.
     * El código administrativo puede no ser numérico, así que la regla fiable es que
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

    /** Posicional: [0]=codigo administrativo, [1]=nombre completo, [2]=ci. */
    private AdministrativoDto filaADto(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        AdministrativoDto dto = new AdministrativoDto();
        dto.setCodigoAdministrativo(CsvUtils.get(c, 0));
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

    private Administrativo buscarActivo(Long id) {
        return administrativoDao.findById(id)
                .filter(a -> a.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Administrativo no encontrado"));
    }

    private AdministrativoDetalleDto toDetalleDto(Administrativo a) {
        AdministrativoDetalleDto dto = new AdministrativoDetalleDto();
        dto.setIdAdministrativo(a.getIdAdministrativo());
        dto.setCodigoAdministrativo(a.getCodigoAdministrativo());
        dto.setEstado(a.getEstado().name());
        Persona p = a.getPersona();
        dto.setIdPersona(p.getIdPersona());
        dto.setNombreCompleto(p.getNombreCompleto());
        dto.setCi(p.getCi());
        ticketDao.findFirstByAdministrativoIdAdministrativoAndEstado(a.getIdAdministrativo(), EstadoRegistro.ACTIVO)
                .ifPresent(t -> {
                    dto.setIdTicket(t.getIdTicket());
                    dto.setCodigoTicket(t.getCodigoIdentificacion());
                });
        return dto;
    }
}
