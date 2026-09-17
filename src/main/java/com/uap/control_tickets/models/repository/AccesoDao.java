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
}
