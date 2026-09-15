package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import com.uap.control_tickets.enums.TipoAcceso;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Registro (log) de una validacion de acceso: cada vez que se escanea el QR de
 * un ticket y se registra una ENTRADA o SALIDA queda una fila aqui.
 *
 * Es el historial que respalda el monitoreo en tiempo real. El usuario CONTROL
 * que hizo el escaneo queda guardado por la auditoria (_registro_id_usuario).
 */
@Entity
@Table(name = "acceso")
@Getter
@Setter
public class Acceso extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_acceso")
    private Long idAcceso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ticket", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoAcceso tipo;

    /** Momento exacto del escaneo. */
    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;
}
