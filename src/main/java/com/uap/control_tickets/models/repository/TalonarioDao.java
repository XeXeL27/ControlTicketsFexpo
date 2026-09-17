package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.enums.TipoTalonario;
import com.uap.control_tickets.models.entity.Talonario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TalonarioDao extends JpaRepository<Talonario, Long> {

    List<Talonario> findAllByEstadoOrderByTipoAscNumeroDesdeAsc(EstadoRegistro estado);

    List<Talonario> findAllByTipoAndEstadoOrderByNumeroDesdeAsc(TipoTalonario tipo, EstadoRegistro estado);

    /** Talonarios asignados a una vendedora (lo unico que deberia poder tocar). */
    List<Talonario> findAllByUsuarioAsignadoIdUsuarioAndEstadoOrderByTipoAscNumeroDesdeAsc(
            Long idUsuario, EstadoRegistro estado);

    boolean existsByNombreAndEstado(String nombre, EstadoRegistro estado);

    /**
     * Talonarios del MISMO tipo cuyo rango se pisa con [desde, hasta].
     *
     * Dos rangos se solapan si cada uno empieza antes de que el otro termine.
     * Se usa para rechazar el alta: si se solapan, hay boletos que pertenecen a dos
     * talonarios a la vez y el cuadre de ventas no cierra nunca.
     */
    @Query("""
            select t from Talonario t
            where t.tipo = :tipo and t.estado = :estado
              and t.numeroDesde <= :hasta and t.numeroHasta >= :desde
              and (:idExcluir is null or t.idTalonario <> :idExcluir)
            """)
    List<Talonario> solapados(@Param("tipo") TipoTalonario tipo,
                              @Param("desde") Integer desde,
                              @Param("hasta") Integer hasta,
                              @Param("estado") EstadoRegistro estado,
                              @Param("idExcluir") Long idExcluir);

    /** Ultimo numero usado por un tipo, para encadenar talonarios nuevos sin huecos. */
    @Query("""
            select coalesce(max(t.numeroHasta), 0) from Talonario t
            where t.tipo = :tipo and t.estado = :estado
            """)
    Integer ultimoNumero(@Param("tipo") TipoTalonario tipo, @Param("estado") EstadoRegistro estado);
}
