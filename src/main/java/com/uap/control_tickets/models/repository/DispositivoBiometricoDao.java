package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.DispositivoBiometrico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DispositivoBiometricoDao extends JpaRepository<DispositivoBiometrico, Long> {

    List<DispositivoBiometrico> findAllByEstado(EstadoRegistro estado);

    List<DispositivoBiometrico> findAllByEstadoAndActivoTrue(EstadoRegistro estado);
}
