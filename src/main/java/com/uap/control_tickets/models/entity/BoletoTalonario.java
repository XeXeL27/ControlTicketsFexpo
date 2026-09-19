package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import com.uap.control_tickets.enums.EstadoVenta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Un boleto dentro de un talonario, para el CONTROL DE VENTAS.
 *
 * Tabla separada de {@link Boleto} (el que se escanea en la puerta) por pedido
 * expreso: aca los numeros se repiten entre tipos y no habria forma de convivir con
 * el UNIQUE(codigo) de aquella tabla.
 *
 * Lo identifica (talonario, numero), NUNCA el numero solo.
 */
@Entity
@Table(name = "boleto_talonario",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_boleto_talonario_numero",
                columnNames = {"id_talonario", "numero"}))
@Getter
@Setter
public class BoletoTalonario extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_boleto_talonario")
    private Long idBoletoTalonario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_talonario", nullable = false)
    private Talonario talonario;

    /** Numero impreso en el boleto, dentro del rango de su talonario. */
    @Column(name = "numero", nullable = false)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_venta", nullable = false, length = 15,
            columnDefinition = "varchar(15) not null default 'DISPONIBLE'")
    private EstadoVenta estadoVenta = EstadoVenta.DISPONIBLE;

    /**
     * Quien lo marco como vendido y cuando.
     *
     * Van en campos PROPIOS y no en las columnas de auditoria a proposito: la
     * auditoria guarda al ULTIMO que modifico la fila, asi que cualquier cambio
     * posterior (anular, corregir) borraria al vendedor. Aca no se pisa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_venta")
    private Usuario vendidoPor;

    @Column(name = "fecha_venta")
    private Instant fechaVenta;

    /**
     * Estado actual en la puerta: true = el portador está dentro del recinto.
     * Solo lo usa la validación de ingreso al concierto (puesto por número);
     * la venta no lo toca. El historial queda en {@link MovimientoTalonario}.
     */
    @Column(name = "dentro", nullable = false,
            columnDefinition = "boolean not null default false")
    private boolean dentro = false;
}
