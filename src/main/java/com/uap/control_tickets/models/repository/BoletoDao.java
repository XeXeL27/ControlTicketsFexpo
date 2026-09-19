package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.TipoBoleto;
import com.uap.control_tickets.models.entity.Boleto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoletoDao extends JpaRepository<Boleto, Long> {

    List<Boleto> findAllByEstado(EstadoRegistro estado);

    /** El código solo identifica dentro de su tipo (puede repetirse entre tipos). */
    Optional<Boleto> findByTipoAndCodigo(TipoBoleto tipo, String codigo);

    long countByEstado(EstadoRegistro estado);

    long countByDentroTrueAndEstado(EstadoRegistro estado);

    // Desglose del monitoreo por categoría (particular / administrativo / docente).
    long countByDentroTrueAndEstadoAndAdministrativoIsNotNull(EstadoRegistro estado);

    long countByDentroTrueAndEstadoAndDocenteIsNotNull(EstadoRegistro estado);

    long countByDentroTrueAndEstadoAndAdministrativoIsNullAndDocenteIsNull(EstadoRegistro estado);
}
