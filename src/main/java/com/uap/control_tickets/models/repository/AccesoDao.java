package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Acceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccesoDao extends JpaRepository<Acceso, Long> {
    interface TotalesPersona {
        Long getIdPersona();
        Long getEntradas();
        Long getSalidas();
        java.time.Instant getUltimoMovimiento();
    }

    @org.springframework.data.jpa.repository.Query("""
            select a.ticket.persona.idPersona as idPersona,
            sum(case when a.tipo = com.uap.control_tickets.enums.TipoAcceso.ENTRADA then 1 else 0 end) as entradas,
            sum(case when a.tipo = com.uap.control_tickets.enums.TipoAcceso.SALIDA then 1 else 0 end) as salidas,
            max(a.fechaHora) as ultimoMovimiento
            from Acceso a where a.estado = :estado group by a.ticket.persona.idPersona
            """)
    List<TotalesPersona> totalesPorPersona(
            @org.springframework.data.repository.query.Param("estado") EstadoRegistro estado);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "ticket")
    org.springframework.data.domain.Page<Acceso> findAllByTicketPersonaIdPersonaAndEstadoOrderByFechaHoraDescIdAccesoDesc(
            Long idPersona, EstadoRegistro estado, org.springframework.data.domain.Pageable pagina);
    interface UltimaEntrada {
        Long getIdTicket();
        java.time.Instant getEntrada();
    }

    @org.springframework.data.jpa.repository.Query("""
            select a.ticket.idTicket as idTicket, max(a.fechaHora) as entrada
            from Acceso a where a.ticket.dentro = true and a.ticket.estado = :estado
            and a.tipo = com.uap.control_tickets.enums.TipoAcceso.ENTRADA
            group by a.ticket.idTicket
            """)
    List<UltimaEntrada> ultimasEntradasDentro(
            @org.springframework.data.repository.query.Param("estado") EstadoRegistro estado);

    // Historial de un ticket, mas reciente primero.
    List<Acceso> findAllByTicketIdTicketOrderByFechaHoraDesc(Long idTicket);

    // Ultimo movimiento de un ticket (para saber si su proximo escaneo es ENTRADA o SALIDA).
    Optional<Acceso> findTopByTicketIdTicketOrderByFechaHoraDesc(Long idTicket);

    // Historial global, mas reciente primero (para el panel de monitoreo).
    List<Acceso> findAllByEstadoOrderByFechaHoraDesc(EstadoRegistro estado);

    /** Accesos de un rango (el dia en curso), para los contadores del dia. */
    long countByTipoAndEstadoAndFechaHoraBetween(
            com.uap.control_tickets.enums.TipoAcceso tipo, EstadoRegistro estado,
            java.time.Instant desde, java.time.Instant hasta);

    /** ¿Ese ticket ya tiene un movimiento de ese tipo ese día? (duplicado de regularización). */
    boolean existsByTicketIdTicketAndTipoAndEstadoAndFechaHoraBetween(
            Long idTicket,
            com.uap.control_tickets.enums.TipoAcceso tipo, EstadoRegistro estado,
            java.time.Instant desde, java.time.Instant hasta);

    /**
     * ENTRADAS de un rango con su ticket ya cargado (para el detalle nominal
     * del reporte: quién entró, con cuántas entradas y cuándo fue la última).
     */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {
            "ticket", "ticket.persona", "ticket.estudiante",
            "ticket.administrativo", "ticket.docente" })
    @org.springframework.data.jpa.repository.Query("""
            select a from Acceso a
            where a.tipo = :tipo and a.estado = :estado
            and a.ticket.estado = :estado
            and a.fechaHora >= :desde and a.fechaHora < :hasta
            order by a.fechaHora desc
            """)
    List<Acceso> entradasConTicketEnRango(
            @org.springframework.data.repository.query.Param("tipo") com.uap.control_tickets.enums.TipoAcceso tipo,
            @org.springframework.data.repository.query.Param("estado") EstadoRegistro estado,
            @org.springframework.data.repository.query.Param("desde") java.time.Instant desde,
            @org.springframework.data.repository.query.Param("hasta") java.time.Instant hasta);
    /**
     * ENTRADAS de un rango de fechas de UNA categoría de ticket (ESTUDIANTE /
     * ADMINISTRATIVO / DOCENTE / EXTERNO). Base del reporte de ingresos al
     * concierto por día: el ticket vale las tres noches, así que se agrupa por
     * fecha del escaneo, no por categoría del ticket.
     */
    @org.springframework.data.jpa.repository.Query("""
            select count(a) from Acceso a
            where a.tipo = :tipoAcceso and a.estado = :estado
            and a.ticket.categoria = :categoria
            and a.ticket.estado = :estado
            and a.fechaHora >= :desde and a.fechaHora < :hasta
            """)
    long contarPorCategoria(
            @org.springframework.data.repository.query.Param("tipoAcceso") com.uap.control_tickets.enums.TipoAcceso tipoAcceso,
            @org.springframework.data.repository.query.Param("estado") EstadoRegistro estado,
            @org.springframework.data.repository.query.Param("categoria") com.uap.control_tickets.enums.CategoriaTicket categoria,
            @org.springframework.data.repository.query.Param("desde") java.time.Instant desde,
            @org.springframework.data.repository.query.Param("hasta") java.time.Instant hasta);
}
