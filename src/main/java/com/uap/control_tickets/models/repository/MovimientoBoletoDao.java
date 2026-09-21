package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.enums.TipoBoleto;
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

    /** ¿Ese boleto ya tiene un movimiento de ese tipo ese día? (duplicado de regularización). */
    boolean existsByBoletoIdBoletoAndTipoAndEstadoAndFechaHoraBetween(
            Long idBoleto, TipoAcceso tipo, EstadoRegistro estado, Instant desde, Instant hasta);

    /**
     * ENTRADAS de un rango de fechas DENTRO de un tipo de boleto (FERIA o
     * PARQUEO). Base del reporte de ingresos por día: el código se repite
     * entre tipos, así que hay que filtrar por la bolsa (m.boleto.tipo).
     */
    @Query("""
            select count(m) from MovimientoBoleto m
            where m.tipo = :tipoAcceso and m.estado = :estado
            and m.boleto.tipo = :tipoBoleto
            and m.boleto.estado = :estado
            and m.fechaHora >= :desde and m.fechaHora < :hasta
            """)
    long contarPorTipoBoleto(
            @Param("tipoAcceso") TipoAcceso tipoAcceso,
            @Param("estado") EstadoRegistro estado,
            @Param("tipoBoleto") TipoBoleto tipoBoleto,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

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
