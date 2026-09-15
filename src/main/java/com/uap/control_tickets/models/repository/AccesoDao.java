package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Acceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccesoDao extends JpaRepository<Acceso, Long> {

    // Historial de un ticket, mas reciente primero.
    List<Acceso> findAllByTicketIdTicketOrderByFechaHoraDesc(Long idTicket);

    // Ultimo movimiento de un ticket (para saber si su proximo escaneo es ENTRADA o SALIDA).
    Optional<Acceso> findTopByTicketIdTicketOrderByFechaHoraDesc(Long idTicket);

    // Historial global, mas reciente primero (para el panel de monitoreo).
    List<Acceso> findAllByEstadoOrderByFechaHoraDesc(EstadoRegistro estado);
}
