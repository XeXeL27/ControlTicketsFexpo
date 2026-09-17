package com.uap.control_tickets.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Fechas reales de los tres dias de la feria.
 *
 * Van por properties y NO hardcodeadas a proposito: asi se puede cambiar la fecha
 * del evento sin tocar codigo, y sobre todo se puede PROBAR fuera de esos dias
 * poniendo {@code app.feria.validar-dia=false}. Sin ese interruptor, validar el dia
 * dejaria el sistema imposible de probar cualquier otro dia del año.
 */
@Component
@ConfigurationProperties(prefix = "app.feria")
@Getter
@Setter
public class FeriaProperties {

    /** Fecha del DIA_1 de la feria. */
    private LocalDate dia1;

    /** Fecha del DIA_2. */
    private LocalDate dia2;

    /** Fecha del DIA_3. */
    private LocalDate dia3;

    /**
     * Si es false, el escaner NO valida que el boleto corresponda al dia de hoy.
     * Se usa para probar el flujo completo fuera de las fechas del evento.
     */
    private boolean validarDia = true;

    /**
     * Zona horaria con la que se decide "que dia es hoy". Las fechas de los
     * movimientos se guardan como Instant (UTC), asi que sin esto un escaneo de las
     * 21:00 en Bolivia contaria como del dia siguiente.
     */
    private String zonaHoraria = "America/La_Paz";
}
