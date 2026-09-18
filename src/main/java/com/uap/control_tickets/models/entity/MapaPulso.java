package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Acomodo del mapa 3D del Pulso FEXPO, guardado como UN documento.
 *
 * Se guarda como JSON en una sola fila y no como una tabla con una fila por zona
 * a proposito: el acomodo es un documento entero que se edita y se guarda de una
 * vez. Con una fila por zona harian falta altas, bajas y sincronizacion por zona
 * para algo que siempre se escribe completo.
 *
 * Si el mapa nunca se edito, no hay fila: el frontend usa entonces el acomodo que
 * viene escrito en el codigo (ZONAS_BASE), que sigue siendo el original de fabrica.
 */
@Entity
@Table(name = "mapa_pulso")
@Getter
@Setter
public class MapaPulso extends AuditoriaConfig {

    /** Nombre del acomodo. Hoy siempre "principal"; deja lugar a varias versiones. */
    public static final String PRINCIPAL = "principal";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mapa")
    private Long idMapa;

    @Column(name = "nombre", nullable = false, unique = true, length = 40)
    private String nombre = PRINCIPAL;

    /**
     * El acomodo completo en JSON: por cada zona su posicion, tamaño, alto,
     * rotacion y color. Va como TEXT porque lo produce y lo consume el frontend;
     * el backend no necesita entenderlo para guardarlo.
     */
    @Column(name = "contenido", nullable = false, columnDefinition = "text")
    private String contenido;
}
