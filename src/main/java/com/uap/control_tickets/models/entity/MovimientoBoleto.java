package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import com.uap.control_tickets.enums.TipoAcceso;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Registro (log) de una validacion de boleto: cada vez que se escanea/tipea el
 * codigo de un boleto de feria y se registra una ENTRADA o SALIDA queda una
 * fila aqui. Es el historial que respalda el monitoreo en tiempo real. El
 * usuario CONTROL que hizo la validacion queda guardado por la auditoria
 * (_registro_id_usuario). Mismo patron que {@link Acceso}, para boletos.
 */
@Entity
@Table(name = "movimiento_boleto")
@Getter
@Setter
public class MovimientoBoleto extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Long idMovimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_boleto", nullable = false)
    private Boleto boleto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoAcceso tipo;

    /** Momento exacto de la validacion. */
    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;
}
