package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Boleto de venta para el ingreso a la feria (FEXPO), anónimo: no está atado a
 * una Persona, solo tiene el CÓDIGO impreso en el ticket físico ya vendido.
 * Se da de alta por carga masiva de CSV (un código por fila).
 *
 * 'dentro' es el estado actual (para el monitoreo en tiempo real); el
 * historial completo de entradas/salidas queda en {@link MovimientoBoleto}.
 */
@Entity
@Table(name = "boleto")
@Getter
@Setter
public class Boleto extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_boleto")
    private Long idBoleto;

    @Column(name = "codigo", nullable = false, unique = true, length = 40)
    private String codigo;

    /** Estado actual: true = el portador esta dentro del recinto. */
    @Column(name = "dentro", nullable = false, columnDefinition = "boolean not null default false")
    private boolean dentro = false;
}
