package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import com.uap.control_tickets.enums.DestinoTalonario;
import com.uap.control_tickets.enums.TipoTalonario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Talonario de boletos de la feria: un bloque correlativo de numeros de un tipo.
 *
 * Ej: "Talonario A, FERIA / EVENTO_1, del 1 al 200". De cada combinacion hay
 * MUCHOS talonarios (A: 1-200, B: 201-400, ...).
 *
 * La numeracion la parte el par (destino, tipo) y no el tipo solo: CONCIERTO,
 * FERIA y PARQUEO tienen cada uno su serie, y dentro de cada destino cada evento
 * tambien. Los tres pueden arrancar en 1 sin pisarse.
 *
 * Regla que se valida al crear: dos talonarios con el MISMO destino Y el MISMO
 * tipo no pueden solaparse. Si se solapan, hay boletos que pertenecen a dos
 * talonarios y el cuadre de ventas no cierra nunca. Entre destinos distintos (o
 * entre tipos distintos) el solape es normal y esta permitido.
 *
 * Es un universo SEPARADO de {@link Boleto} (el boleto que se escanea en la puerta):
 * aca solo se controla la venta.
 */
@Entity
@Table(name = "talonario")
@Getter
@Setter
public class Talonario extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_talonario")
    private Long idTalonario;

    /** Nombre o serie con que lo reconoce la gente de ventas ("Talonario A"). */
    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    /**
     * A que se entra con estos boletos. Junto con {@link #tipo} forma el espacio
     * de numeracion.
     *
     * `columnDefinition` con default a proposito: un `not null` pelado lo rechaza
     * Postgres si la tabla ya tiene filas, la columna no se crea y todas las
     * consultas de la entidad pasan a dar 500 (ddl-auto=update solo deja un WARN).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "destino", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'FERIA'")
    private DestinoTalonario destino;

    /** Para que evento del {@link #destino} sirve (evento 1/2/3 o combo). */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoTalonario tipo;

    /** Primer numero del bloque (inclusive). */
    @Column(name = "numero_desde", nullable = false)
    private Integer numeroDesde;

    /** Ultimo numero del bloque (inclusive). */
    @Column(name = "numero_hasta", nullable = false)
    private Integer numeroHasta;

    /**
     * Precio del boleto. OPCIONAL a proposito: todavia no lo definieron, pero el
     * campo ya esta para cuando pidan el cuadre de caja.
     */
    @Column(name = "precio_unitario", precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    /**
     * Usuaria de ventas que tiene asignado este talonario.
     * Si esta en null, solo el ADMINISTRADOR puede operarlo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_asignado")
    private Usuario usuarioAsignado;

    /** Cuantos boletos tiene el talonario (el rango es inclusivo en los dos extremos). */
    @Transient
    public int getCantidad() {
        if (numeroDesde == null || numeroHasta == null) return 0;
        return numeroHasta - numeroDesde + 1;
    }
}
