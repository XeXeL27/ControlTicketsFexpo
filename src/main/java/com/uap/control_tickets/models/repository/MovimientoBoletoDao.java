package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.models.entity.MovimientoBoleto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MovimientoBoletoDao extends JpaRepository<MovimientoBoleto, Long> {

    // Historial de un boleto, mas reciente primero.
    List<MovimientoBoleto> findAllByBoletoIdBoletoOrderByFechaHoraDesc(Long idBoleto);

    long countByTipoAndEstado(TipoAcceso tipo, EstadoRegistro estado);

    /** Ultimo movimiento de un boleto: sirve para saber si su 'dentro' quedo colgado de ayer. */
    java.util.Optional<MovimientoBoleto> findTopByBoletoIdBoletoOrderByFechaHoraDesc(Long idBoleto);

    /**
     * Movimientos de un rango (el dia en curso).
     * Los contadores del monitoreo tienen que ser DEL DIA, no del evento entero: si no,
     * el dia 2 el tablero muestra sumados los ingresos del dia 1.
     */
    long countByTipoAndEstadoAndFechaHoraBetween(
            TipoAcceso tipo, EstadoRegistro estado, Instant desde, Instant hasta);

    interface UltimaEntrada {
        Long getIdBoleto();
        Instant getEntrada();
    }

    @Query("""
            select m.boleto.idBoleto as idBoleto, max(m.fechaHora) as entrada
            from MovimientoBoleto m where m.boleto.dentro = true and m.boleto.estado = :estado
            and m.tipo = com.uap.control_tickets.enums.TipoAcceso.ENTRADA
            group by m.boleto.idBoleto
            """)
    List<UltimaEntrada> ultimasEntradasDentro(@Param("estado") EstadoRegistro estado);
}
