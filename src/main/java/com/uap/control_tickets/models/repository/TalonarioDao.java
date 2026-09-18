package com.uap.control_tickets.models.repository;

import com.uap.control_tickets.enums.DestinoTalonario;
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

    List<Talonario> findAllByEstadoOrderByDestinoAscTipoAscNumeroDesdeAsc(EstadoRegistro estado);

    /** Talonarios asignados a una vendedora (lo unico que deberia poder tocar). */
    List<Talonario> findAllByUsuarioAsignadoIdUsuarioAndEstadoOrderByDestinoAscTipoAscNumeroDesdeAsc(
            Long idUsuario, EstadoRegistro estado);

    /**
     * El nombre se repite libremente entre destinos y entre tipos: "Talonario A"
     * existe en CONCIERTO y en FERIA y son dos talonarios distintos. Lo unico que
     * no puede repetirse es el nombre DENTRO del mismo par (destino, tipo), que es
     * el mismo espacio donde tampoco pueden pisarse los numeros.
     */
    boolean existsByDestinoAndTipoAndNombreIgnoreCaseAndEstado(
            DestinoTalonario destino, TipoTalonario tipo, String nombre, EstadoRegistro estado);

    /**
     * Talonarios del MISMO destino y MISMO tipo cuyo rango se pisa con [desde, hasta].
     *
     * Dos rangos se solapan si cada uno empieza antes de que el otro termine.
     * Se usa para rechazar el alta: si se solapan, hay boletos que pertenecen a dos
     * talonarios a la vez y el cuadre de ventas no cierra nunca.
     *
     * Filtra por los DOS campos a proposito: sin el destino, un talonario de
     * parqueo 1-200 bloquearia el de feria 1-200, que es justamente lo que tiene
     * que poder convivir.
     */
    @Query("""
            select t from Talonario t
            where t.destino = :destino and t.tipo = :tipo and t.estado = :estado
              and t.numeroDesde <= :hasta and t.numeroHasta >= :desde
              and (:idExcluir is null or t.idTalonario <> :idExcluir)
            """)
    List<Talonario> solapados(@Param("destino") DestinoTalonario destino,
                              @Param("tipo") TipoTalonario tipo,
                              @Param("desde") Integer desde,
                              @Param("hasta") Integer hasta,
                              @Param("estado") EstadoRegistro estado,
                              @Param("idExcluir") Long idExcluir);

    /**
     * Ultimo numero usado por un par (destino, tipo), para encadenar talonarios
     * nuevos sin huecos. Cada par lleva su propia cuenta y arranca en 1.
     */
    @Query("""
            select coalesce(max(t.numeroHasta), 0) from Talonario t
            where t.destino = :destino and t.tipo = :tipo and t.estado = :estado
            """)
    Integer ultimoNumero(@Param("destino") DestinoTalonario destino,
                         @Param("tipo") TipoTalonario tipo,
                         @Param("estado") EstadoRegistro estado);
}
