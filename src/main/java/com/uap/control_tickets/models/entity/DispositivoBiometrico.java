package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Biométrico ZKTeco del que se descargan las huellas.
 *
 * El PIN de usuario dentro del equipo ES el RU del estudiante: al sincronizar
 * se trae cada usuario con sus templates y se cruza por RU con la tabla
 * estudiante. Puerto por defecto 4370 (protocolo pull TCP).
 */
@Entity
@Table(name = "dispositivo_biometrico")
@Getter
@Setter
public class DispositivoBiometrico extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dispositivo")
    private Long idDispositivo;

    /** Nombre de fantasía para ubicarlo ("Portería", "Bloque A"…). */
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** IP fija del equipo en la LAN. */
    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    /** Puerto TCP del protocolo pull (4370 en casi todos los ZKTeco). */
    @Column(name = "puerto", nullable = false)
    private Integer puerto = 4370;

    /** Timeout de conexión/lectura en milisegundos. */
    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 8000;

    /** Si está apagado se lo salta al sincronizar (sin borrarlo). */
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    /**
     * Clave de comunicación del equipo (solo dígitos, la que se pone en el
     * teclado del biométrico). Si es null/vacía se conecta sin autenticar.
     * Se guarda en claro porque el protocolo la necesita para el handshake.
     */
    @Column(name = "clave_comunicacion", length = 20)
    private String claveComunicacion;
}
