package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoHuellaDetalle;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.SincronizacionHuellaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SincronizacionHuellaDetalleDao extends JpaRepository<SincronizacionHuellaDetalle, Long> {

    List<SincronizacionHuellaDetalle> findAllBySincronizacionIdSincronizacionAndEstadoOrderByIdDetalle(
            Long idSincronizacion, EstadoRegistro estado);

    List<SincronizacionHuellaDetalle> findAllBySincronizacionIdSincronizacionAndEstadoAndResultadoOrderByIdDetalle(
            Long idSincronizacion, EstadoHuellaDetalle resultado, EstadoRegistro estado);

    long countBySincronizacionIdSincronizacionAndEstadoAndResultado(
            Long idSincronizacion, EstadoHuellaDetalle resultado, EstadoRegistro estado);
}
