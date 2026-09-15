package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.Utils.ticket.DatosTicketEstudiante;
import com.uap.control_tickets.Utils.ticket.TicketRenderer;
import com.uap.control_tickets.dto.ticket.EmisionMasivaDto;
import com.uap.control_tickets.dto.ticket.ResumenImpresionDto;
import com.uap.control_tickets.enums.FormatoPliego;
import com.uap.control_tickets.dto.ticket.TicketDetalleDto;
import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.Administrativo;
import com.uap.control_tickets.models.entity.Estudiante;
import com.uap.control_tickets.models.entity.Ticket;
import com.uap.control_tickets.models.repository.AdministrativoDao;
import com.uap.control_tickets.models.repository.EstudianteDao;
import com.uap.control_tickets.models.repository.TicketDao;
import com.uap.control_tickets.services.interfaces.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emisión de tickets y generación de su imagen/PDF.
 *
 * "Emitir" crea el registro Ticket con dos identificadores únicos:
 *  - codigoIdentificacion: legible, con prefijo por categoría (EST-000001…).
 *  - qrToken: UUID que se codifica en el QR.
 * "Render" toma el ticket ya emitido y arma el PNG/PDF con la plantilla + QR.
 */
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketDao ticketDao;
    private final EstudianteDao estudianteDao;
    private final AdministrativoDao administrativoDao;
    private final TicketRenderer ticketRenderer;

    /**
     * El propio servicio, pero visto a traves del proxy de Spring. Se usa solo en la
     * emision masiva, para que cada emision abra su transaccion de verdad.
     * Va @Lazy porque si no seria una dependencia circular consigo mismo.
     */
    @Autowired
    @Lazy
    private TicketService self;

    @Override
    @Transactional(readOnly = true)
    public List<TicketDetalleDto> listar() {
        return ticketDao.findAllByEstado(EstadoRegistro.ACTIVO)
                .stream().map(this::toDetalleDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDetalleDto obtener(Long idTicket) {
        return toDetalleDto(buscarActivo(idTicket));
    }

    @Override
    @Transactional
    public TicketDetalleDto emitirEstudiante(Long idEstudiante) {
        Estudiante estudiante = estudianteDao.findById(idEstudiante)
                .filter(e -> e.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante no encontrado"));

        // Idempotente: si ya tiene ticket, se devuelve el existente.
        var existente = ticketDao.findFirstByEstudianteIdEstudianteAndEstado(
                idEstudiante, EstadoRegistro.ACTIVO);
        if (existente.isPresent()) {
            return toDetalleDto(existente.get());
        }

        Ticket ticket = new Ticket();
        ticket.setCategoria(CategoriaTicket.ESTUDIANTE);
        ticket.setPersona(estudiante.getPersona());
        ticket.setEstudiante(estudiante);
        ticket.setQrToken(nuevoQrToken());
        ticket.setCodigoIdentificacion(nuevoCodigo("EST", CategoriaTicket.ESTUDIANTE));
        ticket.setDentro(false);

        return toDetalleDto(ticketDao.save(ticket));
    }

    /**
     * OJO: este metodo NO lleva @Transactional a proposito.
     *
     * La emision de cada estudiante tiene que ser su propia transaccion: si una
     * falla y deja la transaccion marcada para rollback, atrapar la excepcion no
     * alcanza — todo el lote se perderia al hacer commit. Por eso el bucle llama a
     * emitirEstudiante() a traves de 'self' (el proxy de Spring) y no por 'this':
     * una llamada directa por 'this' se saltea el proxy y con el la transaccion.
     */
    @Override
    public EmisionMasivaDto emitirEstudiantesMasivo(List<Long> idsEstudiante) {
        // Sin lista explicita se toman todos los estudiantes activos.
        List<Estudiante> objetivo = (idsEstudiante == null || idsEstudiante.isEmpty())
                ? estudianteDao.findAllByEstado(EstadoRegistro.ACTIVO)
                : estudianteDao.findAllById(idsEstudiante).stream()
                        .filter(e -> e.getEstado() == EstadoRegistro.ACTIVO)
                        .toList();

        EmisionMasivaDto resultado = new EmisionMasivaDto();
        resultado.setTotalEstudiantes(objetivo.size());

        for (Estudiante e : objetivo) {
            // Saber de antemano si ya tenia ticket permite distinguir "emitido" de
            // "omitido": emitirEstudiante() es idempotente y devuelve el existente.
            boolean yaTenia = ticketDao.findFirstByEstudianteIdEstudianteAndEstado(
                    e.getIdEstudiante(), EstadoRegistro.ACTIVO).isPresent();
            try {
                self.emitirEstudiante(e.getIdEstudiante());
                if (yaTenia) {
                    resultado.setOmitidos(resultado.getOmitidos() + 1);
                } else {
                    resultado.setEmitidos(resultado.getEmitidos() + 1);
                }
            } catch (NegocioException ex) {
                resultado.agregarError(e.getIdEstudiante(), nombre(e), ex.getMessage());
            } catch (Exception ex) {
                resultado.agregarError(e.getIdEstudiante(), nombre(e),
                        "Error inesperado: " + ex.getMessage());
            }
        }
        return resultado;
    }

    private String nombre(Estudiante e) {
        var p = e.getPersona();
        return p == null ? "" : (p.getNombre() + " " + (p.getPaterno() == null ? "" : p.getPaterno())).trim();
    }

    // -------------------------------------------------------------------------
    // Impresion por tandas
    // -------------------------------------------------------------------------

    /**
     * Por ahora solo la categoria ESTUDIANTE tiene plantilla de arte cargada.
     * Las demas (ADMINISTRATIVO, EXTERNO) se pueden emitir y contar, pero todavia no
     * se pueden imprimir hasta que llegue su arte.
     */
    private boolean plantillaDisponible(CategoriaTicket categoria) {
        return categoria == CategoriaTicket.ESTUDIANTE;
    }

    /**
     * Normaliza el filtro de carrera: devuelve null (= todas) si no vino, si vino vacio
     * o si la categoria no es ESTUDIANTE (solo los estudiantes tienen carrera).
     * No se recorta ni se pasa a minusculas: el frontend manda el valor tal cual esta
     * guardado, y la consulta lo compara exacto.
     */
    private static String filtroCarrera(CategoriaTicket categoria, String carrera) {
        if (categoria != CategoriaTicket.ESTUDIANTE || carrera == null || carrera.isBlank()) {
            return null;
        }
        return carrera;
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenImpresionDto resumenImpresion(FormatoPliego formato, CategoriaTicket categoria, String carrera) {
        String c = filtroCarrera(categoria, carrera);
        long impresos = c == null
                ? ticketDao.countByCategoriaAndImpresoAndEstado(categoria, true, EstadoRegistro.ACTIVO)
                : ticketDao.countByCategoriaAndEstudianteCarreraAndImpresoAndEstado(categoria, c, true, EstadoRegistro.ACTIVO);
        long pendientes = c == null
                ? ticketDao.countByCategoriaAndImpresoAndEstado(categoria, false, EstadoRegistro.ACTIVO)
                : ticketDao.countByCategoriaAndEstudianteCarreraAndImpresoAndEstado(categoria, c, false, EstadoRegistro.ACTIVO);

        ResumenImpresionDto r = new ResumenImpresionDto();
        r.setCategoria(categoria.name());
        r.setCarrera(c);
        r.setPlantillaDisponible(plantillaDisponible(categoria));
        r.setTotal(impresos + pendientes);
        r.setImpresos(impresos);
        r.setPendientes(pendientes);
        r.setFormato(formato.name());
        r.setPorHoja(formato.getPorHoja());
        r.setLargoCm(formato.getLargoCm());
        r.setAltoCm(formato.getAltoCm());
        // Division hacia arriba: la ultima hoja puede ir incompleta.
        r.setHojasPendientes((int) Math.ceil(pendientes / (double) formato.getPorHoja()));
        return r;
    }

    /**
     * Reordena los tickets segun la lista de ids que manda el frontend (el orden en que
     * se ven en la tabla). El orden solo cambia la POSICION en el pliego: que tickets
     * entran lo sigue decidiendo el backend (pendientes, carrera). Los que no vengan en
     * la lista van al final, por idTicket.
     */
    private static List<Ticket> ordenarSegun(List<Ticket> tickets, List<Long> orden) {
        if (orden == null || orden.isEmpty()) {
            return tickets;
        }
        Map<Long, Integer> posicion = new HashMap<>();
        for (int i = 0; i < orden.size(); i++) {
            posicion.putIfAbsent(orden.get(i), i);
        }
        List<Ticket> ordenados = new ArrayList<>(tickets);
        ordenados.sort(Comparator
                .comparing((Ticket t) -> posicion.getOrDefault(t.getIdTicket(), Integer.MAX_VALUE))
                .thenComparing(Ticket::getIdTicket));
        return ordenados;
    }

    /**
     * Tickets que pueden ir al pliego, en orden de emision.
     * carrera == null → toda la categoria; si no, solo los estudiantes de esa carrera.
     */
    private List<Ticket> candidatosPliego(CategoriaTicket categoria, String carrera, boolean soloPendientes) {
        if (carrera == null) {
            return soloPendientes
                    ? ticketDao.findAllByCategoriaAndImpresoFalseAndEstadoOrderByIdTicketAsc(categoria, EstadoRegistro.ACTIVO)
                    : ticketDao.findAllByCategoriaAndEstado(categoria, EstadoRegistro.ACTIVO);
        }
        return soloPendientes
                ? ticketDao.findAllByCategoriaAndEstudianteCarreraAndImpresoFalseAndEstadoOrderByIdTicketAsc(
                        categoria, carrera, EstadoRegistro.ACTIVO)
                : ticketDao.findAllByCategoriaAndEstudianteCarreraAndEstadoOrderByIdTicketAsc(
                        categoria, carrera, EstadoRegistro.ACTIVO);
    }

    @Override
    @Transactional
    public byte[] generarPliego(FormatoPliego formato, CategoriaTicket categoria, String carrera,
                                Integer cantidad, boolean soloPendientes, boolean marcar,
                                List<Long> orden) {
        if (!plantillaDisponible(categoria)) {
            throw new NegocioException(
                    "La plantilla de arte de " + categoria + " todavia no esta cargada; "
                            + "por ahora solo se puede imprimir la categoria ESTUDIANTE.");
        }

        String c = filtroCarrera(categoria, carrera);
        List<Ticket> candidatos = candidatosPliego(categoria, c, soloPendientes);

        if (candidatos.isEmpty()) {
            String donde = c == null ? "esta categoria" : "la carrera \"" + c + "\"";
            throw new NegocioException(soloPendientes
                    ? "No quedan tickets pendientes de imprimir en " + donde
                    : "No hay tickets emitidos en " + donde);
        }
        // Primero se ordenan como se ven en la tabla (si vino el orden) y DESPUES se
        // recorta la tanda: asi una tanda de N toma los primeros N de la lista.
        candidatos = ordenarSegun(candidatos, orden);

        // Se recorta a la cantidad pedida para poder imprimir de a tandas.
        if (cantidad != null && cantidad > 0 && cantidad < candidatos.size()) {
            candidatos = candidatos.subList(0, cantidad);
        }

        List<DatosTicketEstudiante> datos = candidatos.stream()
                .map(this::datosEstudiante)
                .toList();
        byte[] pdf = ticketRenderer.pdfPliegoEstudiantes(datos, formato);

        // Solo se marcan DESPUES de que el PDF se genero bien: si algo falla a mitad
        // de camino, los tickets siguen pendientes y se pueden volver a intentar.
        if (marcar) {
            Instant ahora = Instant.now();
            for (Ticket t : candidatos) {
                t.setImpreso(true);
                t.setFechaImpresion(ahora);
            }
            ticketDao.saveAll(candidatos);
        }
        return pdf;
    }

    @Override
    @Transactional
    public void marcarImpreso(Long idTicket, boolean impreso) {
        Ticket t = buscarActivo(idTicket);
        t.setImpreso(impreso);
        t.setFechaImpresion(impreso ? Instant.now() : null);
        ticketDao.save(t);
    }

    @Override
    @Transactional
    public int reiniciarImpresion(CategoriaTicket categoria) {
        List<Ticket> impresos = ticketDao.findAllByCategoriaAndImpresoAndEstadoOrderByIdTicketAsc(
                categoria, true, EstadoRegistro.ACTIVO);
        for (Ticket t : impresos) {
            t.setImpreso(false);
            t.setFechaImpresion(null);
        }
        ticketDao.saveAll(impresos);
        return impresos.size();
    }

    @Override
    @Transactional
    public TicketDetalleDto emitirAdministrativo(Long idAdministrativo) {
        Administrativo admin = administrativoDao.findById(idAdministrativo)
                .filter(a -> a.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Administrativo no encontrado"));

        var existente = ticketDao.findFirstByAdministrativoIdAdministrativoAndEstado(
                idAdministrativo, EstadoRegistro.ACTIVO);
        if (existente.isPresent()) {
            return toDetalleDto(existente.get());
        }

        Ticket ticket = new Ticket();
        ticket.setCategoria(CategoriaTicket.ADMINISTRATIVO);
        ticket.setPersona(admin.getPersona());
        ticket.setAdministrativo(admin);
        ticket.setQrToken(nuevoQrToken());
        ticket.setCodigoIdentificacion(nuevoCodigo("ADM", CategoriaTicket.ADMINISTRATIVO));
        ticket.setDentro(false);

        return toDetalleDto(ticketDao.save(ticket));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderPng(Long idTicket) {
        return ticketRenderer.pngEstudiante(datosEstudiante(buscarActivo(idTicket)));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderPdf(Long idTicket) {
        return ticketRenderer.pdfEstudiante(datosEstudiante(buscarActivo(idTicket)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Arma los datos a imprimir a partir del ticket. Solo estudiante por ahora. */
    private DatosTicketEstudiante datosEstudiante(Ticket t) {
        if (t.getCategoria() != CategoriaTicket.ESTUDIANTE || t.getEstudiante() == null) {
            throw new NegocioException(
                    "Solo hay plantilla de estudiante por ahora; este ticket es " + t.getCategoria());
        }
        Estudiante e = t.getEstudiante();
        // En el ticket se imprime la carrera; si no hay, se usa la facultad.
        String carrera = e.getCarrera() != null ? e.getCarrera() : e.getFacultad();
        return new DatosTicketEstudiante(
                t.getPersona().getNombreCompleto(),
                e.getRu(),
                carrera,
                t.getCodigoIdentificacion(),
                t.getQrToken());
    }

    /** Código legible único: PREFIJO-000001. Arranca del conteo y salta colisiones. */
    private String nuevoCodigo(String prefijo, CategoriaTicket categoria) {
        long n = ticketDao.countByCategoria(categoria) + 1;
        String codigo;
        do {
            codigo = String.format("%s-%06d", prefijo, n);
            n++;
        } while (ticketDao.existsByCodigoIdentificacion(codigo));
        return codigo;
    }

    private String nuevoQrToken() {
        String token;
        do {
            token = UUID.randomUUID().toString();
        } while (ticketDao.existsByQrToken(token));
        return token;
    }

    private Ticket buscarActivo(Long idTicket) {
        return ticketDao.findById(idTicket)
                .filter(t -> t.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ticket no encontrado"));
    }

    private TicketDetalleDto toDetalleDto(Ticket t) {
        TicketDetalleDto dto = new TicketDetalleDto();
        dto.setIdTicket(t.getIdTicket());
        dto.setCategoria(t.getCategoria().name());
        dto.setCodigoIdentificacion(t.getCodigoIdentificacion());
        dto.setQrToken(t.getQrToken());
        dto.setDentro(t.isDentro());
        dto.setImpreso(t.isImpreso());
        dto.setFechaImpresion(t.getFechaImpresion());

        dto.setIdPersona(t.getPersona().getIdPersona());
        dto.setNombreCompleto(t.getPersona().getNombreCompleto());
        dto.setCi(t.getPersona().getCi());

        if (t.getEstudiante() != null) {
            dto.setRu(t.getEstudiante().getRu());
            dto.setFacultad(t.getEstudiante().getFacultad());
            dto.setCarrera(t.getEstudiante().getCarrera());
        }
        if (t.getAdministrativo() != null) {
            dto.setCodigoAdministrativo(t.getAdministrativo().getCodigoAdministrativo());
        }
        return dto;
    }
}
