package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import com.uap.control_tickets.enums.EstadoHuellaDetalle;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Una fila del reporte final: qué pasó con cada PIN (RU) leído de los equipos.
 *
 * Sin auditoría de usuario (la escribe la tarea async, no una persona), pero
 * hereda _estado/_fechas por convención del proyecto. No se borra nunca: es el
 * historial de la sincronización.
 */
@Entity
@Table(name = "sincronizacion_huella_detalle",
        indexes = {
                @Index(name = "idx_sinc_det_sinc", columnList = "id_sincronizacion"),
                @Index(name = "idx_sinc_det_estado", columnList = "id_sincronizacion, resultado")
        })
@Getter
@Setter
public class SincronizacionHuellaDetalle extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Long idDetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sincronizacion", nullable = false)
    private SincronizacionHuella sincronizacion;

    /** PIN leído del equipo (= RU esperado). */
    @Column(name = "ru", nullable = false, length = 30)
    private String ru;

    /** Equipo donde apareció ("Portería (192.168.1.201)"). */
    @Column(name = "equipo", length = 150)
    private String equipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 20)
    private EstadoHuellaDetalle resultado;

    /** Detalle legible ("template dedo 2 guardado", "RU no registrado"…). */
    @Column(name = "mensaje", length = 500)
    private String mensaje;
}
