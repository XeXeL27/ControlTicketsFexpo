package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.RegistroSalida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroSalidaDao extends JpaRepository<RegistroSalida, Long> {

    /** El último registro de ese boleto: es el que se muestra al reingresar. */
    Optional<RegistroSalida> findTopByBoletoIdBoletoAndEstadoOrderByIdRegistroDesc(
            Long idBoleto, EstadoRegistro estado);

    /** Historial completo de un boleto (puede haber salido varias veces). */
    List<RegistroSalida> findAllByBoletoIdBoletoAndEstadoOrderByIdRegistroDesc(
            Long idBoleto, EstadoRegistro estado);

    long countBySinDatosAndEstado(boolean sinDatos, EstadoRegistro estado);
}
