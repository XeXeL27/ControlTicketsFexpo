package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.Utils.csv.CsvUtils;
import com.uap.control_tickets.dto.boleto.BoletoDetalleDto;
import com.uap.control_tickets.dto.boleto.BoletoDto;
import com.uap.control_tickets.dto.boleto.PrevisualizacionBoletoCsvDto;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Boleto;
import com.uap.control_tickets.models.repository.BoletoDao;
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

                if (dto.getCodigo() == null) {
                    fp.setEstado("Falta el código");
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
            return previo; // ya existe y está activo: no se toca (preserva dentro/fuera)
        }

        Boleto b = previo != null ? previo : new Boleto();
        b.setEstado(EstadoRegistro.ACTIVO);
        b.setCodigo(codigo);
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
    private BoletoDto filaADto(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        BoletoDto dto = new BoletoDto();
        dto.setCodigo(CsvUtils.get(c, 0));
        return dto;
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
        return dto;
    }
}
