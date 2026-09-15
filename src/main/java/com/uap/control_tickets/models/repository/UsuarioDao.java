package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.models.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioDao extends JpaRepository<Usuario, Long> {

    // Usado por Spring Security para cargar el usuario al loguear.
    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    List<Usuario> findAllByEstado(EstadoRegistro estado);

    // Verificar username unico al actualizar (excluye el propio registro).
    boolean existsByUsernameAndIdUsuarioNot(String username, Long idUsuario);
}
