package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.models.entity.MapaPulso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MapaPulsoDao extends JpaRepository<MapaPulso, Long> {
    Optional<MapaPulso> findByNombre(String nombre);
}
