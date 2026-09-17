package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Administrativo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdministrativoDao extends JpaRepository<Administrativo, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Administrativo e where e.idAdministrativo = :id")
    Optional<Administrativo> buscarParaCambio(@org.springframework.data.repository.query.Param("id") Long id);


    List<Administrativo> findAllByEstado(EstadoRegistro estado);

    Optional<Administrativo> findByCodigoAdministrativo(String codigoAdministrativo);

    Optional<Administrativo> findByPersonaIdPersonaAndEstado(Long idPersona, EstadoRegistro estado);

    boolean existsByCodigoAdministrativo(String codigoAdministrativo);

    boolean existsByCodigoAdministrativoAndIdAdministrativoNot(String codigo, Long idAdministrativo);

    boolean existsByPersonaIdPersona(Long idPersona);
}
