package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.Utils.csv.CsvUtils;
import com.uap.control_tickets.dto.boleto.BoletoDetalleDto;
import com.uap.control_tickets.dto.boleto.BoletoDto;
import com.uap.control_tickets.dto.boleto.PrevisualizacionBoletoCsvDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.enums.DiaFeria;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Administrativo;
import com.uap.control_tickets.models.entity.Boleto;
import com.uap.control_tickets.models.entity.Docente;
import com.uap.control_tickets.models.entity.Persona;
import com.uap.control_tickets.models.repository.AdministrativoDao;
import com.uap.control_tickets.models.repository.BoletoDao;
import com.uap.control_tickets.models.repository.DocenteDao;
import com.uap.control_tickets.models.repository.MovimientoBoletoDao;
import com.uap.control_tickets.services.interfaces.BoletoService;
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
 * CRUD de Boleto (boletos de venta para ingresar a la feria) + importación masiva
 * por CSV: un código por fila, sin datos personales (son anónimos, ya impresos y
 * vendidos). Reimportar el mismo listado no falla: los códigos que ya existen se
 * saltean (no se duplican ni se toca su estado dentro/fuera).
 *
 * Comparte con estudiante/administrativo/docente la parte delicada del CSV
 * (codificación, separador, encabezado) vía {@link CsvUtils}.
 */
@Service
@RequiredArgsConstructor
public class BoletoServiceImpl implements BoletoService {

    private final BoletoDao boletoDao;
    private final MovimientoBoletoDao movimientoBoletoDao;
    private final AdministrativoDao administrativoDao;
    private final DocenteDao docenteDao;

    /** Rótulos habituales en la primera celda del CSV, ya normalizados. */
    private static final Set<String> PALABRAS_ENCABEZADO =
            Set.of("codigo", "codigoboleto", "boleto", "boletos", "ticket", "item", "nro", "n");

    private static final int FILAS_PREVIA = 15;

    @Override
    @Transactional(readOnly = true)
    public List<BoletoDetalleDto> listar() {
        return boletoDao.findAllByEstado(EstadoRegistro.ACTIVO)
                .stream().map(this::toDetalleDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BoletoDetalleDto obtener(Long id) {
        return toDetalleDto(buscarActivo(id));
    }

    @Override
    @Transactional
    public BoletoDetalleDto crear(BoletoDto dto) {
        return toDetalleDto(crearBoleto(dto, false));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Boleto b = buscarActivo(id);
        b.setEstado(EstadoRegistro.ELIMINADO);
        boletoDao.save(b);
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
                    BoletoDto dto = filaADto(linea, sep);
                    boolean yaExistia = boletoDao.findByCodigo(
                            dto.getCodigo() == null ? "" : dto.getCodigo().trim()).isPresent();
                    crearBoleto(dto, true);
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
    public PrevisualizacionBoletoCsvDto previsualizarCsv(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("El archivo CSV está vacío");
        }
        String[] decodificado;
        try {
            decodificado = CsvUtils.decodificarConNombre(archivo.getBytes());
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }

        PrevisualizacionBoletoCsvDto p = new PrevisualizacionBoletoCsvDto();
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

                BoletoDto dto = filaADto(linea, sep);
                PrevisualizacionBoletoCsvDto.FilaPrevia fp = new PrevisualizacionBoletoCsvDto.FilaPrevia();
                fp.setFila(fila);
                fp.setCodigo(dto.getCodigo());
                if (dto.getDiaFeria() != null) fp.setDiaFeria(dto.getDiaFeria().name());

                if (dto.getCodigo() == null) {
                    fp.setEstado("Falta el código");
                    p.setConProblemas(p.getConProblemas() + 1);
                } else if (dto.getDiaFeria() == null) {
                    // Sin día el boleto no se puede validar en la puerta: se avisa
                    // ANTES de importar, no después.
                    fp.setEstado("Falta el día (1, 2 o 3)");
                    p.setConProblemas(p.getConProblemas() + 1);
                } else if (boletoDao.findByCodigo(dto.getCodigo()).isPresent()) {
                    fp.setEstado("YA_EXISTE");
                    p.setExistentes(p.getExistentes() + 1);
                } else {
                    fp.setEstado("NUEVO");
                    p.setNuevos(p.getNuevos() + 1);
                }

                if (p.getFilas().size() < FILAS_PREVIA) p.getFilas().add(fp);
            }
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }
        return p;
    }

    // -------------------------------------------------------------------------
    // Asociación de boletos a administrativos/docentes (3 por persona: 1 por
    // día de la feria). Se entregan junto con su ticket QR, pero el sistema
    // recién se entera acá de qué códigos son. CSV de 4 columnas posicionales:
    // código de la persona, código boleto día 1, día 2, día 3.
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public ImportacionResultadoDto importarAsociacionAdministrativos(MultipartFile archivo) {
        return importarAsociacion(archivo,
                codigo -> administrativoDao.findByCodigoAdministrativo(codigo)
                        .filter(a -> a.getEstado() == EstadoRegistro.ACTIVO).orElse(null),
                "administrativo",
                (boleto, admin) -> {
                    boleto.setAdministrativo((Administrativo) admin);
                    boleto.setDocente(null);
                },
                boleto -> boleto.getAdministrativo() != null || boleto.getDocente() != null);
    }

    @Override
    @Transactional
    public ImportacionResultadoDto importarAsociacionDocentes(MultipartFile archivo) {
        return importarAsociacion(archivo,
                codigo -> docenteDao.findByCodigoDocente(codigo)
                        .filter(d -> d.getEstado() == EstadoRegistro.ACTIVO).orElse(null),
                "docente",
                (boleto, docente) -> {
                    boleto.setDocente((Docente) docente);
                    boleto.setAdministrativo(null);
                },
                boleto -> boleto.getAdministrativo() != null || boleto.getDocente() != null);
    }

    /**
     * Motor común de la asociación: por cada fila busca a la persona (por su
     * código) y asocia hasta 3 boletos (uno por día; una celda vacía se
     * saltea, no es obligatorio tener los 3 todavía). Si el código de boleto
     * ya existe como boleto de venta suelta (sin asociar), lo asocia; si ya
     * existe pero YA está asociado a OTRA persona, es un conflicto y se
     * reporta como error de esa fila (no se pisa la asociación existente).
     * Si el código no existía, se crea (igual que la carga suelta).
     */
    private ImportacionResultadoDto importarAsociacion(
            MultipartFile archivo,
            java.util.function.Function<String, Object> buscarPersona,
            String etiquetaPersona,
            java.util.function.BiConsumer<Boleto, Object> asociar,
            java.util.function.Predicate<Boleto> yaAsociado) {
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

        DiaFeria[] dias = { DiaFeria.DIA_1, DiaFeria.DIA_2, DiaFeria.DIA_3 };

        try (BufferedReader br = new BufferedReader(new StringReader(contenido))) {
            String linea;
            int fila = 0;
            char sep = ',';
            boolean primera = true;
            while ((linea = br.readLine()) != null) {
                if (primera) {
                    linea = CsvUtils.quitarBom(linea);
                    sep = CsvUtils.detectarSeparador(linea);
                    primera = false;
                    String[] c0 = CsvUtils.separar(linea, sep);
                    if (c0.length > 0 && CsvUtils.normalizar(c0[0]).contains("codigo")) continue; // encabezado
                }
                if (linea.isBlank()) continue;
                fila++;
                resultado.setTotalFilas(resultado.getTotalFilas() + 1);

                String[] c = CsvUtils.separar(linea, sep);
                String codigoPersona = CsvUtils.get(c, 0);
                if (codigoPersona == null || codigoPersona.isBlank()) {
                    resultado.agregarError(fila, "Falta el código del " + etiquetaPersona);
                    continue;
                }
                codigoPersona = codigoPersona.trim();

                Object persona = buscarPersona.apply(codigoPersona);
                if (persona == null) {
                    resultado.agregarError(fila, "No existe un " + etiquetaPersona + " activo con código '" + codigoPersona + "'");
                    continue;
                }

                for (int i = 0; i < 3; i++) {
                    String codBoleto = CsvUtils.get(c, i + 1);
                    if (codBoleto == null || codBoleto.isBlank()) continue; // día sin boleto todavía: se permite
                    codBoleto = codBoleto.trim();

                    try {
                        Boleto previo = boletoDao.findByCodigo(codBoleto).orElse(null);
                        boolean existiaActivo = previo != null && previo.getEstado() == EstadoRegistro.ACTIVO;

                        if (existiaActivo && yaAsociado.test(previo)) {
                            boolean esLaMisma = codigoMismaPersona(previo, persona);
                            if (!esLaMisma) {
                                resultado.agregarError(fila, "El código '" + codBoleto
                                        + "' ya está asociado a otro administrativo/docente");
                                continue;
                            }
                            // ya asociado a la misma persona (reimport): solo corrige el día si hiciera falta.
                            previo.setDiaFeria(dias[i]);
                            boletoDao.save(previo);
                            resultado.setActualizados(resultado.getActualizados() + 1);
                            continue;
                        }

                        Boleto b = existiaActivo ? previo : (previo != null ? previo : new Boleto());
                        boolean esNuevo = previo == null;
                        b.setEstado(EstadoRegistro.ACTIVO);
                        b.setCodigo(codBoleto);
                        if (esNuevo) b.setDentro(false);
                        asociar.accept(b, persona);
                        b.setDiaFeria(dias[i]);
                        boletoDao.save(b);
                        if (esNuevo) {
                            resultado.setCreados(resultado.getCreados() + 1);
                        } else {
                            resultado.setActualizados(resultado.getActualizados() + 1);
                        }
                    } catch (Exception ex) {
                        resultado.agregarError(fila, "Código '" + codBoleto + "': " + ex.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }
        return resultado;
    }

    private boolean codigoMismaPersona(Boleto b, Object persona) {
        if (persona instanceof Administrativo a) {
            return b.getAdministrativo() != null
                    && b.getAdministrativo().getIdAdministrativo().equals(a.getIdAdministrativo());
        }
        if (persona instanceof Docente d) {
            return b.getDocente() != null && b.getDocente().getIdDocente().equals(d.getIdDocente());
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Alta común
    // -------------------------------------------------------------------------

    /**
     * Alta común del boleto.
     *
     * @param actualizar si es true (importación CSV) y el código ya existe activo,
     *                   NO falla: simplemente se saltea (no se toca dentro/fuera).
     *                   En el alta individual va false: ahí un código repetido es error.
     */
    private Boleto crearBoleto(BoletoDto dto, boolean actualizar) {
        String codigo = req(dto.getCodigo(), "código");

        Boleto previo = boletoDao.findByCodigo(codigo).orElse(null);
        if (previo != null && previo.getEstado() == EstadoRegistro.ACTIVO) {
            if (!actualizar) {
                throw new NegocioException("Ya existe un boleto con el código '" + codigo + "'");
            }
            // Reimportar CORRIGE el día (así se arregla un archivo mal cargado),
            // pero NO toca 'dentro': si la persona está adentro, sigue adentro.
            if (dto.getDiaFeria() != null && dto.getDiaFeria() != previo.getDiaFeria()) {
                previo.setDiaFeria(dto.getDiaFeria());
                boletoDao.save(previo);
            }
            return previo;
        }

        Boleto b = previo != null ? previo : new Boleto();
        b.setEstado(EstadoRegistro.ACTIVO);
        b.setCodigo(codigo);
        // El día es obligatorio para los sueltos: un boleto sin día no se puede
        // validar en la puerta y entraría cualquier día del evento.
        if (dto.getDiaFeria() == null) {
            throw new NegocioException("Falta el día del boleto '" + codigo + "' (1, 2 o 3)");
        }
        b.setDiaFeria(dto.getDiaFeria());
        if (previo == null) b.setDentro(false); // alta nueva: arranca afuera
        return boletoDao.save(b);
    }

    // -------------------------------------------------------------------------
    // Helpers de parseo CSV
    // -------------------------------------------------------------------------

    private boolean esEncabezado(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        if (c.length == 0) return false;
        return PALABRAS_ENCABEZADO.contains(CsvUtils.normalizar(c[0]));
    }

    /** Posicional: [0]=codigo (única columna esperada). */
    /** CSV de boletos sueltos: `codigo, dia`. El día acepta 1/2/3 o DIA_1/DIA_2/DIA_3. */
    private BoletoDto filaADto(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        BoletoDto dto = new BoletoDto();
        dto.setCodigo(CsvUtils.get(c, 0));
        dto.setDiaFeria(parsearDia(CsvUtils.get(c, 1)));
        return dto;
    }

    /**
     * Interpreta la columna del día con tolerancia: en los archivos reales viene
     * como "1", "dia 1", "DIA_1" o "día 1". Devuelve null si está vacía.
     */
    private DiaFeria parsearDia(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String n = CsvUtils.normalizar(valor); // minúsculas, sin tildes ni signos
        if (n.endsWith("1") ) return DiaFeria.DIA_1;
        if (n.endsWith("2")) return DiaFeria.DIA_2;
        if (n.endsWith("3")) return DiaFeria.DIA_3;
        throw new NegocioException("Día no reconocido: '" + valor + "'. Use 1, 2 o 3.");
    }

    // -------------------------------------------------------------------------
    // Helpers varios
    // -------------------------------------------------------------------------

    private String req(String v, String campo) {
        if (v == null || v.isBlank()) throw new NegocioException("El campo '" + campo + "' es obligatorio");
        return v.trim();
    }

    private Boleto buscarActivo(Long id) {
        return boletoDao.findById(id)
                .filter(b -> b.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Boleto no encontrado"));
    }

    private BoletoDetalleDto toDetalleDto(Boleto b) {
        BoletoDetalleDto dto = new BoletoDetalleDto();
        dto.setIdBoleto(b.getIdBoleto());
        dto.setCodigo(b.getCodigo());
        dto.setEstado(b.getEstado().name());
        dto.setDentro(b.isDentro());
        movimientoBoletoDao.findAllByBoletoIdBoletoOrderByFechaHoraDesc(b.getIdBoleto())
                .stream().findFirst().ifPresent(m -> {
                    dto.setUltimoTipo(m.getTipo().name());
                    dto.setUltimaFecha(m.getFechaHora());
                });
        aplicarIdentificacion(b, dto::setCategoria, dto::setCodigoPersona, dto::setNombrePersona);
        if (b.getDiaFeria() != null) dto.setDiaFeria(b.getDiaFeria().name());
        return dto;
    }

    /**
     * Vuelca la identificación de un boleto (PARTICULAR/ADMINISTRATIVO/DOCENTE +
     * código + nombre) en los setters que le pasen; la comparten el listado, la
     * validación (ValidacionBoletoDto) y el evento en vivo (EventoBoletoDto).
     */
    static void aplicarIdentificacion(
            Boleto b,
            java.util.function.Consumer<String> setCategoria,
            java.util.function.Consumer<String> setCodigoPersona,
            java.util.function.Consumer<String> setNombrePersona) {
        if (b.getAdministrativo() != null) {
            setCategoria.accept("ADMINISTRATIVO");
            setCodigoPersona.accept(b.getAdministrativo().getCodigoAdministrativo());
            setNombrePersona.accept(nombreCompleto(b.getAdministrativo().getPersona()));
        } else if (b.getDocente() != null) {
            setCategoria.accept("DOCENTE");
            setCodigoPersona.accept(b.getDocente().getCodigoDocente());
            setNombrePersona.accept(nombreCompleto(b.getDocente().getPersona()));
        } else {
            setCategoria.accept("PARTICULAR");
        }
    }

    static String nombreCompleto(Persona p) {
        String n = String.join(" ",
                p.getNombre() == null ? "" : p.getNombre(),
                p.getPaterno() == null ? "" : p.getPaterno(),
                p.getMaterno() == null ? "" : p.getMaterno());
        return n.trim().replaceAll("\\s+", " ");
    }
}
