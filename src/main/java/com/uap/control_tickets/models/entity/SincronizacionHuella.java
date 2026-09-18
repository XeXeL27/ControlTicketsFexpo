package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import com.uap.control_tickets.enums.DireccionSincronizacion;
import com.uap.control_tickets.enums.EstadoSincronizacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Cabecera de una sincronización de huellas (un "job").
 *
 * Se crea en estado EN_CURSO al pulsar Sincronizar y la tarea async la va
 * actualizando (procesados, contadores) a medida que recorre los equipos: de
 * ahí leen el polling y el WebSocket para la barra de progreso real.
 * El detalle por RU vive en {@link SincronizacionHuellaDetalle}.
 */
@Entity
@Table(name = "sincronizacion_huella")
@Getter
@Setter
public class SincronizacionHuella extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sincronizacion")
    private Long idSincronizacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_job", nullable = false, length = 20)
    private EstadoSincronizacion estadoJob = EstadoSincronizacion.EN_CURSO;

    /** BAJADA = equipo→sistema, SUBIDA = sistema→equipo (carga masiva). */
    @Enumerated(EnumType.STRING)
    @Column(name = "direccion", nullable = false, length = 10)
    private DireccionSincronizacion direccion = DireccionSincronizacion.BAJADA;

    /** Total de usuarios (suma de los equipos) contra el que avanza la barra. */
    @Column(name = "total_usuarios", nullable = false)
    private Integer totalUsuarios = 0;

    @Column(name = "procesados", nullable = false)
    private Integer procesados = 0;

    @Column(name = "correctos", nullable = false)
    private Integer correctos = 0;

    @Column(name = "duplicados", nullable = false)
    private Integer duplicados = 0;

    @Column(name = "no_encontrados", nullable = false)
    private Integer noEncontrados = 0;

    @Column(name = "sin_huella", nullable = false)
    private Integer sinHuella = 0;

    @Column(name = "errores", nullable = false)
    private Integer errores = 0;

    /** Equipos incluidos, para saber qué se barrió ("Portería, Bloque A"). */
    @Column(name = "equipos", length = 500)
    private String equipos;

    /** Si el job terminó en ERROR global, el motivo acá. */
    @Column(name = "mensaje_error", length = 500)
    private String mensajeError;

    @Column(name = "fecha_fin")
    private Instant fechaFin;
}
