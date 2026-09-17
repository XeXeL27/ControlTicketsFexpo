package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Template de huella de un estudiante, descargado del biométrico.
 *
 * Un estudiante puede tener hasta 10 filas (una por dedo, 0-9). El template se
 * guarda en Base64 tal como lo entrega el equipo (SSR_GetUserTmpStr): así sirve
 * después para comparar o para subirlo a otro equipo sin re-enrolar.
 * El flag rápido {@code Estudiante.tieneHuella} evita joins para los filtros.
 */
@Entity
@Table(name = "huella_digital",
        uniqueConstraints = @UniqueConstraint(name = "uq_huella_estudiante_dedo",
                columnNames = { "id_estudiante", "dedo" }))
@Getter
@Setter
public class HuellaDigital extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_huella")
    private Long idHuella;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante estudiante;

    /** Índice de dedo ZKTeco (0-9). */
    @Column(name = "dedo", nullable = false)
    private Integer dedo;

    /** Template en Base64 (típico 1-2 KB; TEXT para no quedarse corto). */
    @Column(name = "template", nullable = false, columnDefinition = "TEXT")
    private String template;

    /** Versión biométrica que reportó el equipo (9, 10…), si la informó. */
    @Column(name = "version_biometrica", length = 20)
    private String versionBiometrica;

    /** Nombre/IP del equipo del que se descargó (trazabilidad). */
    @Column(name = "equipo_origen", length = 100)
    private String equipoOrigen;

    @Column(name = "fecha_captura")
    private Instant fechaCaptura;
}
