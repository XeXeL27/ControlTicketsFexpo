package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de Persona. Al extender JpaRepository ya tenemos gratis
 * save(), findById(), findAll(), delete(), etc. Los metodos de abajo los
 * genera Spring solo, leyendo su nombre (query methods).
 */
@Repository
public interface PersonaDao extends JpaRepository<Persona, Long> {

    List<Persona> findAllByEstado(EstadoRegistro estado);

    Optional<Persona> findByCi(String ci);

    boolean existsByCi(String ci);

    // Para validar CI unico al editar, sin chocar con el propio registro.
    boolean existsByCiAndIdPersonaNot(String ci, Long idPersona);
}
