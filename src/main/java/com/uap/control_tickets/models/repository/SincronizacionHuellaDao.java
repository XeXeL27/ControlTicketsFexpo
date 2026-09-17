package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.SincronizacionHuella;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SincronizacionHuellaDao extends JpaRepository<SincronizacionHuella, Long> {

    List<SincronizacionHuella> findAllByEstadoOrderByIdSincronizacionDesc(EstadoRegistro estado);
}
