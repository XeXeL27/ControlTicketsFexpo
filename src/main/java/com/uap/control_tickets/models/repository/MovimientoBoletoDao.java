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
