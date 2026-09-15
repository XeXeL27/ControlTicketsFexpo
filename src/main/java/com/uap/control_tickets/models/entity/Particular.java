package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Particular (externo): asistente que compra su ticket en puerta.
 * Sus datos (nombre completo + CI) se cargan a mano en una Persona.
 * Aqui se guarda solo lo propio de la venta.
 */
@Entity
@Table(name = "particular")
@Getter
@Setter
public class Particular extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_particular")
    private Long idParticular;

    /** Monto pagado por el ticket (opcional; la fecha de venta la da la auditoria). */
    @Column(name = "monto_venta", precision = 10, scale = 2)
    private BigDecimal montoVenta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", nullable = false)
    private Persona persona;
}
