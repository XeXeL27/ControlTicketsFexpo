package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.talonario.*;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.EstadoVenta;
import com.uap.control_tickets.enums.TipoTalonario;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.BoletoTalonario;
import com.uap.control_tickets.models.entity.Talonario;
import com.uap.control_tickets.models.entity.Usuario;
import com.uap.control_tickets.models.repository.BoletoTalonarioDao;
import com.uap.control_tickets.models.repository.TalonarioDao;
import com.uap.control_tickets.models.repository.UsuarioDao;
import com.uap.control_tickets.services.interfaces.TalonarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Control de venta de boletos por talonario.
 *
 * Universo SEPARADO del Boleto que se escanea en la puerta: aca los numeros se
 * repiten entre tipos (el 250 del EVENTO_1 y el 250 del COMBO son distintos) y lo
 * que identifica a un boleto es (talonario, numero).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalonarioServiceImpl implements TalonarioService {

    private final TalonarioDao talonarioDao;
    private final BoletoTalonarioDao boletoDao;
    private final UsuarioDao usuarioDao;

    @Override
    @Transactional(readOnly = true)
    public List<TalonarioDetalleDto> listar(TipoTalonario tipo, boolean soloMios) {
        List<Talonario> talonarios;
        if (soloMios) {
            Usuario yo = usuarioActual();
            talonarios = talonarioDao.findAllByUsuarioAsignadoIdUsuarioAndEstadoOrderByTipoAscNumeroDesdeAsc(
                    yo.getIdUsuario(), EstadoRegistro.ACTIVO);
            if (tipo != null) talonarios = talonarios.stream().filter(t -> t.getTipo() == tipo).toList();
        } else if (tipo != null) {
            talonarios = talonarioDao.findAllByTipoAndEstadoOrderByNumeroDesdeAsc(tipo, EstadoRegistro.ACTIVO);
        } else {
            talonarios = talonarioDao.findAllByEstadoOrderByTipoAscNumeroDesdeAsc(EstadoRegistro.ACTIVO);
        }
        return talonarios.stream().map(this::toDetalle).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TalonarioDetalleDto obtener(Long idTalonario) {
        return toDetalle(buscar(idTalonario));
    }

    @Override
    @Transactional
    public TalonarioDetalleDto crear(TalonarioDto dto) {
        Talonario t = new Talonario();
        t.setNombre(dto.getNombre().trim());
        t.setTipo(dto.getTipo());
        t.setNumeroDesde(dto.getNumeroDesde());
        t.setNumeroHasta(dto.getNumeroHasta());
        t.setPrecioUnitario(dto.getPrecioUnitario());
        t.setUsuarioAsignado(resolverUsuario(dto.getIdUsuarioAsignado()));

        validarRango(t, null);
        if (talonarioDao.existsByNombreAndEstado(t.getNombre(), EstadoRegistro.ACTIVO)) {
            throw new NegocioException("Ya existe un talonario llamado '" + t.getNombre() + "'");
        }
        talonarioDao.save(t);
        generarBoletos(t);
        return toDetalle(t);
    }

    @Override
    @Transactional
    public List<TalonarioDetalleDto> generar(GeneracionTalonariosDto dto) {
        int cantidad = dto.getCantidadTalonarios();
        int porTalonario = dto.getBoletosPorTalonario();

        // Sin numero inicial, se encadena despues del ultimo del tipo (sin huecos).
        int inicio = dto.getNumeroInicial() != null
                ? dto.getNumeroInicial()
                : talonarioDao.ultimoNumero(dto.getTipo(), EstadoRegistro.ACTIVO) + 1;

        String prefijo = dto.getPrefijoNombre() == null || dto.getPrefijoNombre().isBlank()
                ? "Talonario" : dto.getPrefijoNombre().trim();

        List<TalonarioDetalleDto> creados = new ArrayList<>(cantidad);
        int desde = inicio;
        for (int i = 1; i <= cantidad; i++) {
            int hasta = desde + porTalonario - 1;
            Talonario t = new Talonario();
            t.setTipo(dto.getTipo());
            t.setNumeroDesde(desde);
            t.setNumeroHasta(hasta);
            t.setPrecioUnitario(dto.getPrecioUnitario());
            t.setNombre(nombreLibre(prefijo, desde, hasta));
            validarRango(t, null);
            talonarioDao.save(t);
            generarBoletos(t);
            creados.add(toDetalle(t));
            desde = hasta + 1;
        }
        log.info("Generados {} talonarios de {} boletos para {} (del {} al {}).",
                cantidad, porTalonario, dto.getTipo(), inicio, desde - 1);
        return creados;
    }

    @Override
    @Transactional
    public TalonarioDetalleDto actualizar(Long idTalonario, TalonarioActualizarDto dto) {
        Talonario t = buscar(idTalonario);
        String nombre = dto.getNombre().trim();
        if (!nombre.equalsIgnoreCase(t.getNombre())
                && talonarioDao.existsByNombreAndEstado(nombre, EstadoRegistro.ACTIVO)) {
            throw new NegocioException("Ya existe un talonario llamado '" + nombre + "'");
        }
        t.setNombre(nombre);
        t.setPrecioUnitario(dto.getPrecioUnitario());
        t.setUsuarioAsignado(resolverUsuario(dto.getIdUsuarioAsignado()));
        talonarioDao.save(t);
        return toDetalle(t);
    }

    @Override
    @Transactional
    public void eliminar(Long idTalonario) {
        Talonario t = buscar(idTalonario);
        long vendidos = boletoDao.countByTalonarioIdTalonarioAndEstadoVentaAndEstado(
                idTalonario, EstadoVenta.VENDIDO, EstadoRegistro.ACTIVO);
        if (vendidos > 0) {
            throw new NegocioException("No se puede eliminar '" + t.getNombre() + "': ya tiene "
                    + vendidos + " boleto(s) vendido(s). Anule los boletos si hubo un error de carga.");
        }
        t.setEstado(EstadoRegistro.ELIMINADO);
        talonarioDao.save(t);
        List<BoletoTalonario> boletos = boletoDao
                .findAllByTalonarioIdTalonarioAndEstadoOrderByNumeroAsc(idTalonario, EstadoRegistro.ACTIVO);
        boletos.forEach(b -> b.setEstado(EstadoRegistro.ELIMINADO));
        boletoDao.saveAll(boletos);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoletoTalonarioDto> boletos(Long idTalonario) {
        buscar(idTalonario);
        return boletoDao.findAllByTalonarioIdTalonarioAndEstadoOrderByNumeroAsc(
                        idTalonario, EstadoRegistro.ACTIVO)
                .stream().map(this::toBoletoDto).toList();
    }

    @Override
    @Transactional
    public ResultadoMarcadoDto marcar(MarcarVentaDto dto) {
        Talonario t = buscar(dto.getIdTalonario());
        verificarPuedeOperar(t);

        Set<Integer> numeros = numerosAMarcar(dto, t);
        if (numeros.isEmpty()) {
            throw new NegocioException("No se indicó ningún boleto para marcar");
        }

        // Los que la vendedora nombro uno por uno: sobre esos si puede pasar por
        // encima de un ANULADO, porque escribio ese numero a proposito.
        Set<Integer> explicitos = dto.getNumeros() == null
                ? Set.of()
                : new LinkedHashSet<>(dto.getNumeros());

        ResultadoMarcadoDto r = new ResultadoMarcadoDto();
        r.setSolicitados(numeros.size());
        Usuario yo = usuarioActual();
        Instant ahora = Instant.now();
        List<BoletoTalonario> aGuardar = new ArrayList<>();

        for (Integer n : numeros) {
            BoletoTalonario b = boletoDao.findByTalonarioIdTalonarioAndNumero(t.getIdTalonario(), n)
                    .filter(x -> x.getEstado() == EstadoRegistro.ACTIVO)
                    .orElse(null);
            if (b == null) {
                r.aviso("El número " + n + " no pertenece a este talonario");
                continue;
            }
            if (b.getEstadoVenta() == dto.getEstado()) {
                r.setSinCambios(r.getSinCambios() + 1);
                continue;
            }
            // Un boleto ANULADO (roto, mojado) no debe revivir porque la vendedora
            // vuelva a rendir el rango: "vendidos hasta el 137" pasa por encima de el
            // sin querer. Solo se lo saca de anulado nombrandolo EXPLICITAMENTE en la
            // lista de numeros sueltos, que es un acto deliberado.
            if (b.getEstadoVenta() == EstadoVenta.ANULADO && !explicitos.contains(n)) {
                r.aviso("El número " + n + " está anulado y no se tocó "
                        + "(para revertirlo, márquelo individualmente)");
                continue;
            }
            b.setEstadoVenta(dto.getEstado());
            if (dto.getEstado() == EstadoVenta.VENDIDO) {
                b.setVendidoPor(yo);
                b.setFechaVenta(ahora);
            } else if (dto.getEstado() == EstadoVenta.DISPONIBLE) {
                // Volver a disponible limpia la venta: si no, queda un vendedor fantasma.
                b.setVendidoPor(null);
                b.setFechaVenta(null);
            }
            aGuardar.add(b);
        }
        boletoDao.saveAll(aGuardar);
        r.setCambiados(aGuardar.size());
        return r;
    }

    // ---------------- helpers ----------------

    /** Arma el conjunto de numeros a marcar segun la forma que haya usado la vendedora. */
    private Set<Integer> numerosAMarcar(MarcarVentaDto dto, Talonario t) {
        Set<Integer> numeros = new LinkedHashSet<>();
        if (dto.getHastaNumero() != null) {
            for (int n = t.getNumeroDesde(); n <= dto.getHastaNumero(); n++) numeros.add(n);
        }
        if (dto.getDesde() != null && dto.getHasta() != null) {
            if (dto.getDesde() > dto.getHasta()) {
                throw new NegocioException("El número inicial no puede ser mayor que el final");
            }
            for (int n = dto.getDesde(); n <= dto.getHasta(); n++) numeros.add(n);
        }
        if (dto.getNumeros() != null) {
            dto.getNumeros().stream().filter(java.util.Objects::nonNull).forEach(numeros::add);
        }
        return numeros;
    }

    /** Crea las filas del rango. Medido: 10.000 filas tardan ~200 ms. */
    private void generarBoletos(Talonario t) {
        List<BoletoTalonario> nuevos = new ArrayList<>(t.getCantidad());
        for (int n = t.getNumeroDesde(); n <= t.getNumeroHasta(); n++) {
            BoletoTalonario b = new BoletoTalonario();
            b.setTalonario(t);
            b.setNumero(n);
            b.setEstadoVenta(EstadoVenta.DISPONIBLE);
            nuevos.add(b);
        }
        boletoDao.saveAll(nuevos);
    }

    /** El rango tiene que ser coherente y no pisarse con otro talonario del MISMO tipo. */
    private void validarRango(Talonario t, Long idExcluir) {
        if (t.getNumeroDesde() > t.getNumeroHasta()) {
            throw new NegocioException("El número inicial no puede ser mayor que el final");
        }
        List<Talonario> choques = talonarioDao.solapados(
                t.getTipo(), t.getNumeroDesde(), t.getNumeroHasta(), EstadoRegistro.ACTIVO, idExcluir);
        if (!choques.isEmpty()) {
            Talonario c = choques.get(0);
            throw new NegocioException("El rango " + t.getNumeroDesde() + "-" + t.getNumeroHasta()
                    + " de " + t.getTipo().etiqueta() + " se solapa con '" + c.getNombre()
                    + "' (" + c.getNumeroDesde() + "-" + c.getNumeroHasta() + ")");
        }
    }

    /** Evita chocar con el UNIQUE del nombre cuando se generan muchos de una. */
    private String nombreLibre(String prefijo, int desde, int hasta) {
        String base = prefijo + " " + desde + "-" + hasta;
        String nombre = base;
        int i = 2;
        while (talonarioDao.existsByNombreAndEstado(nombre, EstadoRegistro.ACTIVO)) {
            nombre = base + " (" + i++ + ")";
        }
        return nombre;
    }

    private Talonario buscar(Long id) {
        return talonarioDao.findById(id)
                .filter(t -> t.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Talonario no encontrado"));
    }

    private Usuario usuarioActual() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioDao.findByUsername(username)
                .orElseThrow(() -> new NegocioException("No se pudo identificar al usuario"));
    }

    private Usuario resolverUsuario(Long idUsuario) {
        if (idUsuario == null) return null;
        return usuarioDao.findById(idUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario asignado no encontrado"));
    }

    /**
     * Una vendedora solo puede tocar SUS talonarios; el administrador, todos.
     * Sin esto, "el talonario que le corresponda" seria una convencion y no un control.
     */
    private void verificarPuedeOperar(Talonario t) {
        boolean esAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
        if (esAdmin) return;
        Usuario yo = usuarioActual();
        if (t.getUsuarioAsignado() == null
                || !t.getUsuarioAsignado().getIdUsuario().equals(yo.getIdUsuario())) {
            throw new NegocioException("El talonario '" + t.getNombre() + "' no está asignado a usted");
        }
    }

    private TalonarioDetalleDto toDetalle(Talonario t) {
        TalonarioDetalleDto d = new TalonarioDetalleDto();
        d.setIdTalonario(t.getIdTalonario());
        d.setNombre(t.getNombre());
        d.setTipo(t.getTipo().name());
        d.setTipoEtiqueta(t.getTipo().etiqueta());
        d.setNumeroDesde(t.getNumeroDesde());
        d.setNumeroHasta(t.getNumeroHasta());
        d.setCantidad(t.getCantidad());
        d.setPrecioUnitario(t.getPrecioUnitario());
        if (t.getUsuarioAsignado() != null) {
            d.setIdUsuarioAsignado(t.getUsuarioAsignado().getIdUsuario());
            d.setUsuarioAsignado(t.getUsuarioAsignado().getUsername());
        }
        long id = t.getIdTalonario();
        d.setVendidos(boletoDao.countByTalonarioIdTalonarioAndEstadoVentaAndEstado(id, EstadoVenta.VENDIDO, EstadoRegistro.ACTIVO));
        d.setAnulados(boletoDao.countByTalonarioIdTalonarioAndEstadoVentaAndEstado(id, EstadoVenta.ANULADO, EstadoRegistro.ACTIVO));
        d.setDisponibles(boletoDao.countByTalonarioIdTalonarioAndEstadoVentaAndEstado(id, EstadoVenta.DISPONIBLE, EstadoRegistro.ACTIVO));
        if (t.getPrecioUnitario() != null) {
            d.setMontoVendido(t.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getVendidos())));
        }
        return d;
    }

    private BoletoTalonarioDto toBoletoDto(BoletoTalonario b) {
        BoletoTalonarioDto d = new BoletoTalonarioDto();
        d.setIdBoletoTalonario(b.getIdBoletoTalonario());
        d.setNumero(b.getNumero());
        d.setEstadoVenta(b.getEstadoVenta().name());
        d.setFechaVenta(b.getFechaVenta());
        if (b.getVendidoPor() != null) d.setVendidoPor(b.getVendidoPor().getUsername());
        return d;
    }
}
