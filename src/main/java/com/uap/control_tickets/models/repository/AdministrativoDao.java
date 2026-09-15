package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Administrativo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdministrativoDao extends JpaRepository<Administrativo, Long> {

    List<Administrativo> findAllByEstado(EstadoRegistro estado);

    Optional<Administrativo> findByCodigoAdministrativo(String codigoAdministrativo);

    boolean existsByCodigoAdministrativo(String codigoAdministrativo);

    boolean existsByCodigoAdministrativoAndIdAdministrativoNot(String codigo, Long idAdministrativo);

    boolean existsByPersonaIdPersona(Long idPersona);
}
