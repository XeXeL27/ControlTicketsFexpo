package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.models.entity.MovimientoTalonario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface MovimientoTalonarioDao extends JpaRepository<MovimientoTalonario, Long> {

    /** Último movimiento del boleto (para detectar el 'dentro' colgado de otro día). */
    Optional<MovimientoTalonario> findTopByBoletoTalonarioIdBoletoTalonarioOrderByFechaHoraDesc(
            Long idBoletoTalonario);

    long countByTipoAndEstado(TipoAcceso tipo, EstadoRegistro estado);

    long countByTipoAndEstadoAndFechaHoraBetween(
            TipoAcceso tipo, EstadoRegistro estado, Instant desde, Instant hasta);

    /** ¿Ese boleto ya tiene un movimiento de ese tipo ese día? (duplicado de regularización). */
    boolean existsByBoletoTalonarioIdBoletoTalonarioAndTipoAndEstadoAndFechaHoraBetween(
            Long idBoletoTalonario, TipoAcceso tipo, EstadoRegistro estado,
            Instant desde, Instant hasta);
}
