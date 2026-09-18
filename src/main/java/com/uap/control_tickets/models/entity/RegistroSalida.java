package com.uap.control_tickets.models.entity;

import com.uap.control_tickets.config.AuditoriaConfig;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Datos que se le piden al visitante CUANDO DICE QUE VA A VOLVER a ingresar.
 *
 * Los boletos de venta suelta son anonimos: alguien podria salir y pasarle el
 * boleto a otro. Por eso, al salir, el control pregunta si va a volver y ofrece
 * registrar nombre, CI y foto. **Los tres son opcionales a proposito**: hay gente
 * que no quiere dar sus datos y no se la va a incomodar. Si no da nada, queda
 * {@link #sinDatos} en true como respaldo de que se preguntó.
 *
 * NO se guarda en `persona` a proposito:
 *  - `persona.ci` es UNIQUE y hay miles de filas: un CI repetido o mal tipeado
 *    rompe el alta o asocia el boleto a la persona equivocada.
 *  - `persona.nombre` y `paterno` son NOT NULL: aca puede no haber ningun dato.
 *  - son dos poblaciones distintas (identidad institucional vs visitante de feria).
 *
 * Tabla aparte y no columnas en `boleto` porque la foto es un campo grande y
 * `boleto` se lee entero en cada resumen y en la lista de "quien esta dentro".
 *
 * Una fila POR SALIDA: si la persona sale tres veces quedan las tres, y eso da
 * historial sin trabajo extra. Al reingresar se muestra la mas reciente.
 */
@Entity
@Table(name = "registro_salida")
@Getter
@Setter
public class RegistroSalida extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro")
    private Long idRegistro;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_boleto", nullable = false)
    private Boleto boleto;

    /** La salida puntual en la que se tomaron los datos. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_movimiento")
    private MovimientoBoleto movimiento;

    @Column(name = "nombre", length = 120)
    private String nombre;

    /** Sin UNIQUE: acá el CI es un dato de apoyo, no una identidad del sistema. */
    @Column(name = "ci", length = 20)
    private String ci;

    /**
     * Ruta RELATIVA del archivo dentro de la carpeta de fotos
     * (ej. "2026-09-18/a1b2c3.jpg"). Los bytes NO estan en la BD: viven en el
     * disco y los maneja AlmacenFotos. Aca queda solo el nombre.
     */
    @Column(name = "archivo_foto", length = 200)
    private String archivoFoto;

    /** true = se le preguntó y no quiso dar sus datos. Es el respaldo del control. */
    @Column(name = "sin_datos", nullable = false,
            columnDefinition = "boolean not null default false")
    private boolean sinDatos = false;

    /** ¿Tiene algún dato útil para comparar al reingresar? */
    @Transient
    public boolean tieneDatos() {
        return (nombre != null && !nombre.isBlank())
                || (ci != null && !ci.isBlank())
                || (archivoFoto != null && !archivoFoto.isBlank());
    }
}
