package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.Utils.ticket.DatosTicketEstudiante;
import com.uap.control_tickets.Utils.ticket.DatosTicketAdministrativo;
import com.uap.control_tickets.Utils.ticket.DatosTicketDocente;
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
import com.uap.control_tickets.models.entity.Docente;
import com.uap.control_tickets.models.entity.Estudiante;
import com.uap.control_tickets.models.entity.Ticket;
import com.uap.control_tickets.models.repository.AdministrativoDao;
import com.uap.control_tickets.models.repository.DocenteDao;
import com.uap.control_tickets.models.repository.EstudianteDao;
import com.uap.control_tickets.models.repository.PersonaDao;
import com.uap.control_tickets.models.repository.TicketDao;
import com.uap.control_tickets.Utils.csv.CsvUtils;
import com.uap.control_tickets.dto.estudiante.ImportacionResultadoDto;
import com.uap.control_tickets.services.interfaces.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
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
    private final DocenteDao docenteDao;
    private final PersonaDao personaDao;
    private final TicketRenderer ticketRenderer;
    private final CambioTipoService cambioTipoService;

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
     * Estudiantes tienen arte; administrativos imprimen solo el reverso sin fondo.
     */
    private boolean plantillaDisponible(CategoriaTicket categoria) {
        return categoria == CategoriaTicket.ESTUDIANTE || categoria == CategoriaTicket.ADMINISTRATIVO
                || categoria == CategoriaTicket.DOCENTE;
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
        formato = formatoParaCategoria(formato, categoria);
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
        formato = formatoParaCategoria(formato, categoria);
        if (!plantillaDisponible(categoria)) {
            throw new NegocioException(
                    "La plantilla de arte de " + categoria + " todavia no esta cargada; "
                            + "se pueden imprimir estudiantes, administrativos y docentes.");
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

        byte[] pdf;
        if (categoria == CategoriaTicket.ADMINISTRATIVO) {
            List<DatosTicketAdministrativo> datos = candidatos.stream()
                    .map(this::datosAdministrativo)
                    .toList();
            pdf = ticketRenderer.pdfPliegoAdministrativos(datos, formato);
        } else if (categoria == CategoriaTicket.DOCENTE) {
            pdf = ticketRenderer.pdfPliegoDocentes(candidatos.stream().map(this::datosDocente).toList(), formato);
        } else {
            pdf = ticketRenderer.pdfPliegoEstudiantes(candidatos.stream()
                    .map(this::datosEstudiante).toList(), formato);
        }

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
        Administrativo admin = administrativoDao.buscarParaCambio(idAdministrativo)
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
    @Transactional
    public TicketDetalleDto emitirDocente(Long idDocente) {
        Docente doc = docenteDao.buscarParaCambio(idDocente)
                .filter(d -> d.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Docente no encontrado"));

        var existente = ticketDao.findFirstByDocenteIdDocenteAndEstado(idDocente, EstadoRegistro.ACTIVO);
        if (existente.isPresent()) {
            return toDetalleDto(existente.get());
        }

        Ticket ticket = new Ticket();
        ticket.setCategoria(CategoriaTicket.DOCENTE);
        ticket.setPersona(doc.getPersona());
        ticket.setDocente(doc);
        ticket.setQrToken(nuevoQrToken());
        ticket.setCodigoIdentificacion(nuevoCodigo("DOC", CategoriaTicket.DOCENTE));
        ticket.setDentro(false);

        return toDetalleDto(ticketDao.save(ticket));
    }

    @Override
    @Transactional
    public TicketDetalleDto marcarEntrega(Long idTicket, boolean entregado) {
        Ticket t = buscarActivo(idTicket);
        t.setEntregado(entregado);
        t.setFechaEntrega(entregado ? Instant.now() : null);
        return toDetalleDto(ticketDao.save(t));
    }

    // -------------------------------------------------------------------------
    // Actualización por código administrativo (entrega + posible promoción a docente)
    // -------------------------------------------------------------------------

    /**
     * Busca por código administrativo, marca su ticket como entregado y,
     * si la segunda columna trae texto, lo promueve a docente usando ese texto
     * como materia/carrera (campo Docente.carrera).
     * La tercera columna (SI/NO) decide si se marca entregado.
     *
     * Flujo:
     *  1) Busca Administrativo ACTIVO por código; si no existe busca Docente ACTIVO
     *     con el mismo código (ya fue convertido antes).
     *  2) Si materia no está vacía -> promueve/actualiza a docente (siempre, aunque SI/NO)
     *  3) Solo si col3 = SI marca entregado el/los tickets del registro final (misma fila, QR y código conservados).
     *     Si col3 = NO y hay materia -> solo promueve, NO marca entregado.
     */
    @Override
    @Transactional
    public TicketDetalleDto actualizarPorCodigoAdm(String codigoAdm, String materia, String entregaFlag) {
        if (codigoAdm == null || codigoAdm.isBlank()) {
            throw new NegocioException("El código administrativo es obligatorio");
        }
        String codigo = codigoAdm.trim();
        String carrera = materia == null ? "" : materia.trim();
        boolean marcarEntregado = parseEntregaFlag(entregaFlag);

        // 1) Buscar por código administrativo/docente; si no pilla, buscar por CI (carnet) en Persona
        var admOpt = administrativoDao.findByCodigoAdministrativo(codigo)
                .filter(a -> a.getEstado() == EstadoRegistro.ACTIVO);
        var docOpt = docenteDao.findByCodigoDocente(codigo)
                .filter(d -> d.getEstado() == EstadoRegistro.ACTIVO);

        boolean yaEsDocente = false;
        Long idAdm = null;
        Long idDoc = null;
        Administrativo admEnt = null;
        Docente docEnt = null;
        Long personaId = null;
        String codigoAdmOriginal = null;

        if (admOpt.isPresent()) {
            admEnt = admOpt.get();
            idAdm = admEnt.getIdAdministrativo();
            personaId = admEnt.getPersona().getIdPersona();
            codigoAdmOriginal = admEnt.getCodigoAdministrativo();
        } else if (docOpt.isPresent()) {
            docEnt = docOpt.get();
            yaEsDocente = true;
            idDoc = docEnt.getIdDocente();
            personaId = docEnt.getPersona().getIdPersona();
            codigoAdmOriginal = docEnt.getCodigoDocente();
            if (!carrera.isEmpty() && !carrera.equals(docEnt.getCarrera())) {
                if (carrera.length() > 255) {
                    throw new NegocioException("La materia/carrera supera 255 caracteres");
                }
                docEnt.setCarrera(carrera);
                docenteDao.save(docEnt);
            }
        } else {
            // Fallback: col1 como CI (carnet) -> Persona -> Administrativo/Docente por persona
            var personaOpt = personaDao.findByCi(codigo)
                    .filter(p -> p.getEstado() == EstadoRegistro.ACTIVO);
            if (personaOpt.isPresent()) {
                personaId = personaOpt.get().getIdPersona();
                var admPorCi = administrativoDao.findByPersonaIdPersonaAndEstado(personaId, EstadoRegistro.ACTIVO);
                if (admPorCi.isPresent()) {
                    admEnt = admPorCi.get();
                    idAdm = admEnt.getIdAdministrativo();
                    codigoAdmOriginal = admEnt.getCodigoAdministrativo();
                } else {
                    var docPorCi = docenteDao.findByPersonaIdPersonaAndEstado(personaId, EstadoRegistro.ACTIVO);
                    if (docPorCi.isPresent()) {
                        docEnt = docPorCi.get();
                        yaEsDocente = true;
                        idDoc = docEnt.getIdDocente();
                        codigoAdmOriginal = docEnt.getCodigoDocente();
                        if (!carrera.isEmpty() && !carrera.equals(docEnt.getCarrera())) {
                            if (carrera.length() > 255) {
                                throw new NegocioException("La materia/carrera supera 255 caracteres");
                            }
                            docEnt.setCarrera(carrera);
                            docenteDao.save(docEnt);
                        }
                    }
                }
            }
            if (idAdm == null && idDoc == null) {
                throw new RecursoNoEncontradoException(
                        "No se encontró persona con código/CI: " + codigo + " (se buscó como código adm/docente y como carnet CI)");
            }
        }

        // 2) Si trae materia y aún es administrativo -> promover a docente (independiente de SI/NO)
        if (!carrera.isEmpty() && !yaEsDocente) {
            cambioTipoService.aDocente(idAdm, carrera);
            // Tras la promoción el ticket ya migró a docente; buscar por personaId (más fiable que por código si se buscó por CI)
            var docente = (personaId != null
                    ? docenteDao.findByPersonaIdPersonaAndEstado(personaId, EstadoRegistro.ACTIVO).orElse(null)
                    : null);
            if (docente == null && codigoAdmOriginal != null) {
                docente = docenteDao.findByCodigoDocente(codigoAdmOriginal)
                        .filter(d -> d.getEstado() == EstadoRegistro.ACTIVO).orElse(null);
            }
            if (docente == null) {
                docente = docenteDao.findByCodigoDocente(codigo)
                        .filter(d -> d.getEstado() == EstadoRegistro.ACTIVO).orElse(null);
            }
            if (docente == null) {
                throw new RecursoNoEncontradoException("Docente no encontrado tras conversión");
            }
            idDoc = docente.getIdDocente();
            docEnt = docente;
            yaEsDocente = true;
        }

        // 3) Marcar entregado solo si col3 = SI
        if (!marcarEntregado) {
            // NO + sin materia = fila sin acción (no entrega ni promueve) -> informar
            if (carrera.isEmpty()) {
                throw new NegocioException("Fila sin acción: col3=NO y sin materia — no se marca entrega ni se promueve");
            }
            // Solo promovió: devolver el ticket del docente sin tocar entregado, o dto sintético si no hay ticket
            List<Ticket> tickets = yaEsDocente
                    ? ticketDao.findAllByDocenteIdDocente(idDoc)
                    : ticketDao.findAllByAdministrativoIdAdministrativo(idAdm);
            var activo = tickets.stream().filter(t -> t.getEstado() == EstadoRegistro.ACTIVO).findFirst().orElse(null);
            if (activo != null) return toDetalleDto(activo);
            // Sin ticket pero ya es docente promovido: devolver dto sintético con datos de persona/docente
            if (docEnt != null) return dtoSinteticoDocente(docEnt);
            var docente = docenteDao.findByCodigoDocente(codigo).orElse(null);
            if (docente != null) return dtoSinteticoDocente(docente);
            // Fallback por personaId si se buscó por CI
            if (personaId != null) {
                var docentePorPersona = docenteDao.findByPersonaIdPersonaAndEstado(personaId, EstadoRegistro.ACTIVO).orElse(null);
                if (docentePorPersona != null) return dtoSinteticoDocente(docentePorPersona);
            }
            if (!tickets.isEmpty()) return toDetalleDto(tickets.get(0));
            throw new NegocioException("Promovido a docente sin ticket emitido (ticket pendiente de emitir)");
        }

        List<Ticket> tickets;
        if (yaEsDocente) {
            tickets = ticketDao.findAllByDocenteIdDocente(idDoc);
        } else {
            tickets = ticketDao.findAllByAdministrativoIdAdministrativo(idAdm);
        }

        if (tickets.isEmpty()) {
            throw new NegocioException(
                    "La persona con código '" + codigo + "' no tiene ticket emitido; emítalo primero");
        }

        Instant ahora = Instant.now();
        for (Ticket t : tickets) {
            if (t.getEstado() == EstadoRegistro.ACTIVO) {
                t.setEntregado(true);
                t.setFechaEntrega(ahora);
            }
        }
        ticketDao.saveAll(tickets);

        return toDetalleDto(tickets.stream()
                .filter(t -> t.getEstado() == EstadoRegistro.ACTIVO)
                .findFirst().orElse(tickets.get(0)));
    }

    /** Compatibilidad 2 params -> SI por defecto. */
    @Override
    @Transactional
    public TicketDetalleDto actualizarPorCodigoAdm(String codigoAdm, String materia) {
        return actualizarPorCodigoAdm(codigoAdm, materia, "SI");
    }

    private boolean parseEntregaFlag(String flag) {
        if (flag == null || flag.isBlank()) return true; // compatibilidad: sin col3 = SI
        String n = flag.trim().toLowerCase().replace("í", "i");
        n = n.replaceAll("[^a-z0-9]", "");
        if (n.equals("si") || n.equals("s") || n.equals("yes") || n.equals("y") || n.equals("1") || n.equals("true") || n.equals("entregado") || n.equals("entregar")) return true;
        if (n.equals("no") || n.equals("n") || n.equals("0") || n.equals("false") || n.equals("pendiente") || n.equals("noentregar")) return false;
        throw new NegocioException("Columna 3 debe ser SI o NO (recibido: '" + flag + "')");
    }

    private TicketDetalleDto dtoSinteticoDocente(com.uap.control_tickets.models.entity.Docente docente) {
        TicketDetalleDto dto = new TicketDetalleDto();
        dto.setCategoria(CategoriaTicket.DOCENTE.name());
        dto.setCodigoDocente(docente.getCodigoDocente());
        dto.setCarrera(docente.getCarrera());
        var p = docente.getPersona();
        if (p != null) {
            dto.setIdPersona(p.getIdPersona());
            dto.setNombreCompleto(p.getNombreCompleto());
            dto.setCi(p.getCi());
        }
        dto.setEntregado(false);
        return dto;
    }

    @Override
    public ImportacionResultadoDto actualizarPorCodigoAdmCsv(MultipartFile archivo) {
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

        // Cada fila: codigo_adm, materia (col 2 opcional), entrega SI/NO (col3). Se usa el mismo decoder/separador/BOM que importaciones.
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
                    if (esEncabezadoActualizacion(linea, sep)) continue;
                }
                if (linea.isBlank()) continue;
                fila++;
                resultado.setTotalFilas(resultado.getTotalFilas() + 1);
                try {
                    String[] c = CsvUtils.separar(linea, sep);
                    String codigo = CsvUtils.get(c, 0);
                    String materia = CsvUtils.get(c, 1); // segunda columna opcional
                    String entrega = CsvUtils.get(c, 2); // tercera columna SI/NO
                    if (codigo == null || codigo.isBlank()) {
                        throw new NegocioException("Falta el código administrativo en columna 1");
                    }
                    // Cada fila en su propia transacción para que un fallo no tumbe el lote
                    self.actualizarPorCodigoAdm(codigo, materia, entrega);
                    // Conteos: creados = entregados (SI), actualizados = promovidos a docente (con materia)
                    boolean promovido = materia != null && !materia.isBlank();
                    boolean entregado = entrega == null || entrega.isBlank() || parseEntregaFlag(entrega);
                    if (promovido) resultado.setActualizados(resultado.getActualizados() + 1);
                    if (entregado) resultado.setCreados(resultado.getCreados() + 1);
                    // Si NO + con materia: solo promovido, sin entregar -> solo actualizados (ya contado)
                    // Si SI sin materia: solo creados (ya contado)
                    // Si SI con materia: ambos contadores incrementan
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

    private boolean esEncabezadoActualizacion(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        if (c.length == 0) return false;
        String primera = CsvUtils.normalizar(c[0]);
        if (primera.contains("codigo") || primera.equals("cod") || primera.equals("codigoadm")
                || primera.equals("item") || primera.equals("nro") || primera.equals("n")) {
            return true;
        }
        String segunda = c.length > 1 ? CsvUtils.normalizar(c[1]) : "";
        if (segunda.contains("materia") || segunda.contains("carrera") || segunda.contains("docente")) {
            return true;
        }
        String tercera = c.length > 2 ? CsvUtils.normalizar(c[2]) : "";
        if (tercera.contains("entrega") || tercera.contains("entregado") || tercera.equals("si") || tercera.equals("no")) {
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Estudiantes: marcar entregado por RU (1 columna)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public TicketDetalleDto marcarEntregaPorRu(String ru) {
        return marcarEntregaPorRu(ru, "SI");
    }

    @Override
    @Transactional
    public TicketDetalleDto marcarEntregaPorRu(String ru, String entregaFlag) {
        if (ru == null || ru.isBlank()) {
            throw new NegocioException("El RU es obligatorio");
        }
        String ruTrim = ru.trim();
        boolean marcarEntregado = parseEntregaFlag(entregaFlag);

        Estudiante est = estudianteDao.findByRu(ruTrim)
                .filter(e -> e.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró estudiante con RU: " + ruTrim));

        List<Ticket> tickets = ticketDao.findAllByEstudianteIdEstudiante(est.getIdEstudiante());
        // Filtrar solo ACTIVOS para la respuesta; si no hay ninguno, es que no se emitió ticket
        var activos = tickets.stream().filter(t -> t.getEstado() == EstadoRegistro.ACTIVO).toList();
        if (activos.isEmpty()) {
            throw new NegocioException("El estudiante con RU '" + ruTrim + "' no tiene ticket emitido; emítalo primero");
        }

        if (!marcarEntregado) {
            // NO -> no marca entrega, solo devuelve el ticket tal cual (útil si se quiere verificar sin marcar)
            return toDetalleDto(activos.get(0));
        }

        Instant ahora = Instant.now();
        for (Ticket t : activos) {
            t.setEntregado(true);
            t.setFechaEntrega(ahora);
        }
        ticketDao.saveAll(activos);
        return toDetalleDto(activos.get(0));
    }

    @Override
    public ImportacionResultadoDto marcarEntregaPorRuCsv(MultipartFile archivo) {
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
                    if (esEncabezadoRu(linea, sep)) continue;
                }
                if (linea.isBlank()) continue;
                fila++;
                resultado.setTotalFilas(resultado.getTotalFilas() + 1);
                try {
                    String[] c = CsvUtils.separar(linea, sep);
                    String ru = CsvUtils.get(c, 0);
                    String entrega = CsvUtils.get(c, 1); // opcional SI/NO en col2
                    if (ru == null || ru.isBlank()) {
                        throw new NegocioException("Falta el RU en columna 1");
                    }
                    // Solo si es SI (o vacío) cuenta como entregado; NO no marca pero no es error
                    self.marcarEntregaPorRu(ru, entrega);
                    boolean marcar = entrega == null || entrega.isBlank() || parseEntregaFlag(entrega);
                    if (marcar) {
                        resultado.setCreados(resultado.getCreados() + 1);
                    } else {
                        // NO sin marcar: lo contamos como actualizado para reflejar que se procesó
                        resultado.setActualizados(resultado.getActualizados() + 1);
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

    private boolean esEncabezadoRu(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        if (c.length == 0) return false;
        String primera = CsvUtils.normalizar(c[0]);
        if (primera.equals("ru") || primera.equals("r.u.") || primera.equals("r u") || primera.contains("registro") || primera.contains("universitario")) {
            return true;
        }
        // Si la primera celda no tiene dígitos, es encabezado (RU siempre tiene dígitos)
        String ru = CsvUtils.get(c, 0);
        if (ru != null && ru.chars().noneMatch(Character::isDigit)) return true;
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderPng(Long idTicket) {
        Ticket ticket = buscarActivo(idTicket);
        if (ticket.getCategoria() == CategoriaTicket.ADMINISTRATIVO) {
            return ticketRenderer.pngAdministrativo(datosAdministrativo(ticket));
        }
        if (ticket.getCategoria() == CategoriaTicket.DOCENTE) {
            return ticketRenderer.pngDocente(datosDocente(ticket));
        }
        return ticketRenderer.pngEstudiante(datosEstudiante(ticket));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderPdf(Long idTicket) {
        Ticket ticket = buscarActivo(idTicket);
        if (ticket.getCategoria() == CategoriaTicket.ADMINISTRATIVO) {
            return ticketRenderer.pdfAdministrativo(datosAdministrativo(ticket));
        }
        if (ticket.getCategoria() == CategoriaTicket.DOCENTE) {
            return ticketRenderer.pdfDocente(datosDocente(ticket));
        }
        return ticketRenderer.pdfEstudiante(datosEstudiante(ticket));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private FormatoPliego formatoParaCategoria(FormatoPliego formato, CategoriaTicket categoria) {
        if (categoria == CategoriaTicket.ADMINISTRATIVO) return FormatoPliego.ADMINISTRATIVO_5;
        if (formato == FormatoPliego.ADMINISTRATIVO_5) {
            throw new NegocioException("El formato ADMINISTRATIVO_5 es exclusivo de administrativos");
        }
        return formato;
    }

    private DatosTicketDocente datosDocente(Ticket t) {
        return new DatosTicketDocente(t.getPersona().getNombreCompleto(), t.getPersona().getCi(),
                t.getDocente().getCodigoDocente(), t.getDocente().getCarrera(),
                t.getCodigoIdentificacion(), t.getQrToken());
    }

    /** Arma los datos a imprimir a partir del ticket de administrativo. */
    private DatosTicketAdministrativo datosAdministrativo(Ticket t) {
        if (t.getCategoria() != CategoriaTicket.ADMINISTRATIVO || t.getAdministrativo() == null) {
            throw new NegocioException(
                    "Este ticket no es de administrativo (es " + t.getCategoria() + ")");
        }
        return new DatosTicketAdministrativo(t.getPersona().getNombreCompleto(),
                t.getPersona().getCi(), t.getAdministrativo().getCodigoAdministrativo(),
                t.getQrToken());
    }

    /** Arma los datos a imprimir a partir del ticket de estudiante. */
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
        dto.setEntregado(t.isEntregado());
        dto.setFechaEntrega(t.getFechaEntrega());

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
        if (t.getDocente() != null) {
            dto.setCodigoDocente(t.getDocente().getCodigoDocente());
            dto.setCarrera(t.getDocente().getCarrera());
        }
        return dto;
    }
}
