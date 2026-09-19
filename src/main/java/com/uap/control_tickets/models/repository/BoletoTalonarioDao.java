package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.DestinoTalonario;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.EstadoVenta;
import com.uap.control_tickets.enums.TipoTalonario;
import com.uap.control_tickets.models.entity.BoletoTalonario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BoletoTalonarioDao extends JpaRepository<BoletoTalonario, Long> {

    List<BoletoTalonario> findAllByTalonarioIdTalonarioAndEstadoOrderByNumeroAsc(
            Long idTalonario, EstadoRegistro estado);

    Optional<BoletoTalonario> findByTalonarioIdTalonarioAndNumero(Long idTalonario, Integer numero);

    /**
     * El boleto N del par (destino, tipo), para la puerta: como los rangos no se
     * solapan dentro del mismo par, el número identifica un solo boleto (si la
     * regla se violó a mano, viene más de uno y la puerta toma el primero).
     */
    @Query("""
            select b from BoletoTalonario b
            where b.talonario.destino = :destino and b.talonario.tipo = :tipo
              and b.numero = :numero and b.estado = :estado and b.talonario.estado = :estado
            """)
    List<BoletoTalonario> buscarParaPuerta(@Param("destino") DestinoTalonario destino,
                                           @Param("tipo") TipoTalonario tipo,
                                           @Param("numero") Integer numero,
                                           @Param("estado") EstadoRegistro estado);

    /** Los boletos de un rango dentro del talonario (para marcar de a tandas). */
    List<BoletoTalonario> findAllByTalonarioIdTalonarioAndNumeroBetweenAndEstadoOrderByNumeroAsc(
            Long idTalonario, Integer desde, Integer hasta, EstadoRegistro estado);

    long countByTalonarioIdTalonarioAndEstadoVentaAndEstado(
            Long idTalonario, EstadoVenta estadoVenta, EstadoRegistro estado);

    long countByTalonarioIdTalonarioAndEstado(Long idTalonario, EstadoRegistro estado);

    /** Resumen por tipo de evento, para el consolidado de ventas. */
    interface TotalesTipo {
        TipoTalonario getTipo();
        Long getTotal();
        Long getVendidos();
        Long getAnulados();
    }

    @Query("""
            select b.talonario.tipo as tipo,
                   count(b) as total,
                   sum(case when b.estadoVenta = com.uap.control_tickets.enums.EstadoVenta.VENDIDO then 1 else 0 end) as vendidos,
                   sum(case when b.estadoVenta = com.uap.control_tickets.enums.EstadoVenta.ANULADO then 1 else 0 end) as anulados
            from BoletoTalonario b
            where b.estado = :estado
            group by b.talonario.tipo
            """)
    List<TotalesTipo> totalesPorTipo(@Param("estado") EstadoRegistro estado);
}
