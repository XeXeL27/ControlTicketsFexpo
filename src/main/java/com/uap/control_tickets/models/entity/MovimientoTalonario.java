package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import com.uap.control_tickets.enums.TipoAcceso;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Registro (log) de una validación en puerta de un boleto de talonario: cada
 * vez que se tipea el número en el puesto del concierto y se registra una
 * ENTRADA o SALIDA queda una fila acá. Es el historial que respalda el
 * anti-clones (el flag {@link BoletoTalonario#dentro} es solo el estado actual).
 * El usuario CONTROL que hizo la validación queda guardado por la auditoría
 * (_registro_id_usuario). Mismo patrón que {@link MovimientoBoleto}, para
 * boletos vendidos por talonario.
 */
@Entity
@Table(name = "movimiento_talonario")
@Getter
@Setter
public class MovimientoTalonario extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento_talonario")
    private Long idMovimientoTalonario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_boleto_talonario", nullable = false)
    private BoletoTalonario boletoTalonario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoAcceso tipo;

    /** Momento exacto de la validación. */
    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;
}
