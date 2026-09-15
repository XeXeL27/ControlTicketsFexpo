package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketDao extends JpaRepository<Ticket, Long> {

    // --- Escaneo / validacion ---
    Optional<Ticket> findByQrToken(String qrToken);

    Optional<Ticket> findByCodigoIdentificacion(String codigoIdentificacion);

    boolean existsByQrToken(String qrToken);

    boolean existsByCodigoIdentificacion(String codigoIdentificacion);

    // Ticket ya emitido para un estudiante (para no duplicar).
    Optional<Ticket> findFirstByEstudianteIdEstudianteAndEstado(Long idEstudiante, EstadoRegistro estado);

    // Ticket ya emitido para un administrativo.
    Optional<Ticket> findFirstByAdministrativoIdAdministrativoAndEstado(Long idAdministrativo, EstadoRegistro estado);

    // --- Listados ---
    List<Ticket> findAllByEstado(EstadoRegistro estado);

    List<Ticket> findAllByCategoriaAndEstado(CategoriaTicket categoria, EstadoRegistro estado);

    long countByCategoria(CategoriaTicket categoria);

    // --- Impresion por tandas ---
    /** Pendientes de imprimir, en orden de emision (el codigo EST-0000xx es correlativo). */
    List<Ticket> findAllByImpresoFalseAndEstadoOrderByIdTicketAsc(EstadoRegistro estado);

    List<Ticket> findAllByImpresoAndEstadoOrderByIdTicketAsc(boolean impreso, EstadoRegistro estado);

    long countByImpresoAndEstado(boolean impreso, EstadoRegistro estado);

    // --- Impresion por tandas, ACOTADA a una categoria (cada categoria se imprime aparte) ---
    long countByCategoriaAndImpresoAndEstado(CategoriaTicket categoria, boolean impreso, EstadoRegistro estado);

    List<Ticket> findAllByCategoriaAndImpresoFalseAndEstadoOrderByIdTicketAsc(CategoriaTicket categoria, EstadoRegistro estado);

    List<Ticket> findAllByCategoriaAndImpresoAndEstadoOrderByIdTicketAsc(CategoriaTicket categoria, boolean impreso, EstadoRegistro estado);

    // --- Monitoreo en tiempo real ---
    List<Ticket> findAllByDentroTrueAndEstado(EstadoRegistro estado);

    long countByDentroTrueAndEstado(EstadoRegistro estado);

    long countByEstado(EstadoRegistro estado);
}
