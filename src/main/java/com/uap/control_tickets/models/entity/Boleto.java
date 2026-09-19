package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import com.uap.control_tickets.enums.DiaFeria;
import com.uap.control_tickets.enums.TipoBoleto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Boleto de entrada a la feria (FEXPO), identificado por su CÓDIGO impreso.
 * La mayoría son de venta suelta y anónimos (no atados a una Persona). Pero un
 * administrativo o docente recibe además 3 boletos junto con su ticket QR (uno
 * por día de la feria): esos SÍ quedan asociados acá ({@link #administrativo}/
 * {@link #docente} + {@link #diaFeria}), para poder identificar quién ingresó.
 * Un boleto asociado solo tiene UNA de las dos FK (o ninguna, si es de venta
 * suelta/particular).
 *
 * 'dentro' es el estado actual (para el monitoreo en tiempo real); el
 * historial completo de entradas/salidas queda en {@link MovimientoBoleto}.
 */
@Entity
@Table(name = "boleto", uniqueConstraints =
        // El código se repite entre tipos (el 137 de feria y el 137 de parqueo
        // son dos boletos distintos): lo único es el par (tipo, codigo).
        @UniqueConstraint(name = "boleto_tipo_codigo_unique", columnNames = { "tipo", "codigo" }))
@Getter
@Setter
public class Boleto extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_boleto")
    private Long idBoleto;

    @Column(name = "codigo", nullable = false, length = 40)
    private String codigo;

    /**
     * A qué da ingreso (FERIA o PARQUEO). Los ya cargados son todos FERIA
     * (ver migración en CLAUDE.md §8.2.2).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoBoleto tipo = TipoBoleto.FERIA;

    /** Estado actual: true = el portador esta dentro del recinto. */
    @Column(name = "dentro", nullable = false, columnDefinition = "boolean not null default false")
    private boolean dentro = false;

    /** Solo si este boleto es uno de los 3 entregados a un administrativo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_administrativo")
    private Administrativo administrativo;

    /** Solo si este boleto es uno de los 3 entregados a un docente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_docente")
    private Docente docente;

    /** Día de la feria al que corresponde (solo boletos asociados; null en venta suelta). */
    @Enumerated(EnumType.STRING)
    @Column(name = "dia_feria", length = 10)
    private DiaFeria diaFeria;
}
