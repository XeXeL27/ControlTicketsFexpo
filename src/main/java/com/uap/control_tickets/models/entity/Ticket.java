package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import com.uap.control_tickets.enums.CategoriaTicket;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Ticket de entrada. Es el nucleo del control de ingreso.
 *
 * Identificacion unica (dos formas):
 *  - codigoIdentificacion: codigo legible impreso en el ticket ("CODIGO TICKETS"),
 *    ej. EST-000001 / ADM-000001 / EXT-000001. Unico.
 *  - qrToken: contenido del codigo QR (un UUID). Unico. Es lo que se escanea.
 *
 * Datos del asistente: van en Persona (nombre completo + CI). Segun la categoria,
 * el ticket apunta ademas al registro especifico (Estudiante / Administrativo /
 * Particular), de donde se toman RU, carrera/facultad o codigo administrativo.
 * Solo una de esas tres referencias esta llena, acorde a 'categoria'.
 *
 * 'dentro' es una copia rapida del estado actual (dentro/fuera) para el monitoreo
 * en tiempo real; el historial completo de entradas/salidas queda en Acceso.
 */
@Entity
@Table(name = "ticket")
@Getter
@Setter
public class Ticket extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ticket")
    private Long idTicket;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 20)
    private CategoriaTicket categoria;

    @Column(name = "codigo_identificacion", nullable = false, unique = true, length = 30)
    private String codigoIdentificacion;

    @Column(name = "qr_token", nullable = false, unique = true, length = 100)
    private String qrToken;

    /** Estado actual: true = la persona esta dentro del recinto. */
    @Column(name = "dentro", nullable = false, columnDefinition = "boolean not null default false")
    private boolean dentro = false;

    /**
     * true = el ticket ya salio en un pliego de impresion.
     * Sirve para no reimprimir dos veces al mismo asistente cuando la impresion
     * se hace por tandas (el pliego entra 8 o 5 tickets por hoja).
     */
    @Column(name = "impreso", nullable = false, columnDefinition = "boolean not null default false")
    private boolean impreso = false;

    /** Cuando se genero el pliego que incluyo a este ticket. */
    @Column(name = "fecha_impresion")
    private java.time.Instant fechaImpresion;

    /**
     * true = el ticket físico ya se le entregó a la persona.
     * Control aparte de la impresión: un ticket puede estar impreso pero todavía
     * sin entregar. Quién lo marcó queda en la auditoría (_registro/_modificacion).
     */
    @Column(name = "entregado", nullable = false, columnDefinition = "boolean not null default false")
    private boolean entregado = false;

    /** Cuando se marcó la entrega (null si todavía no se entregó). */
    @Column(name = "fecha_entrega")
    private java.time.Instant fechaEntrega;

    /**
     * true = la persona rechazó / no aceptó recibir la entrada.
     * Excluyente con entregado: no puede estar entregado y rechazado a la vez.
     * Sirve para el reporte de rechazados y para la nómina en rojo.
     */
    @Column(name = "rechazado", nullable = false, columnDefinition = "boolean not null default false")
    private boolean rechazado = false;

    /** Cuando se marcó como rechazado (null si no). */
    @Column(name = "fecha_rechazo")
    private java.time.Instant fechaRechazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", nullable = false)
    private Persona persona;

    // Referencia al registro de categoria. Solo una esta llena segun 'categoria'.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estudiante")
    private Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_administrativo")
    private Administrativo administrativo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_docente")
    private Docente docente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_particular")
    private Particular particular;
}
