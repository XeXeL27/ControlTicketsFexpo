package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.config.FeriaProperties;
import com.uap.control_tickets.enums.DiaFeria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Resuelve "que dia del evento es hoy" y a que dia pertenece un movimiento.
 *
 * Es la unica pieza que sabe de fechas; todo lo demas (validar el dia del boleto,
 * contar los ingresos de hoy, detectar un 'dentro' viejo) le pregunta a esta clase.
 * Asi la regla queda en un solo lugar y se puede apagar entera para las pruebas.
 */
@Service
@RequiredArgsConstructor
public class CalendarioFeria {

    private final FeriaProperties propiedades;

    /** La zona con la que se decide el dia (por defecto la de Bolivia). */
    public ZoneId zona() {
        try {
            return ZoneId.of(propiedades.getZonaHoraria());
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    /** Fecha de hoy segun la zona del evento, no la del servidor. */
    public LocalDate hoy() {
        return LocalDate.now(zona());
    }

    /** A que fecha del calendario cae un instante (util para agrupar movimientos). */
    public LocalDate fechaDe(Instant instante) {
        return instante == null ? null : instante.atZone(zona()).toLocalDate();
    }

    /** true = hay que validar que el boleto sea del dia de hoy. */
    public boolean validacionActiva() {
        return propiedades.isValidarDia();
    }

    /** Fecha configurada para un dia del evento; null si no se configuro. */
    public LocalDate fechaDe(DiaFeria dia) {
        if (dia == null) return null;
        return switch (dia) {
            case DIA_1 -> propiedades.getDia1();
            case DIA_2 -> propiedades.getDia2();
            case DIA_3 -> propiedades.getDia3();
        };
    }

    /**
     * Que dia del evento es hoy, o null si hoy no es ninguno de los tres
     * (o si las fechas no estan configuradas).
     */
    public DiaFeria diaDeHoy() {
        LocalDate hoy = hoy();
        for (DiaFeria d : DiaFeria.values()) {
            LocalDate fecha = fechaDe(d);
            if (fecha != null && fecha.equals(hoy)) return d;
        }
        return null;
    }

    /** Nombre legible para los mensajes de la puerta: "día 1 (18/09)". */
    public String describir(DiaFeria dia) {
        if (dia == null) return "sin día asignado";
        LocalDate f = fechaDe(dia);
        String num = dia.name().replace("DIA_", "día ");
        return f == null ? num : num + " (" + f.getDayOfMonth() + "/" + f.getMonthValue() + ")";
    }

    /**
     * ¿Ese instante es de un dia ANTERIOR a hoy?
     *
     * Con esto se detecta el flag 'dentro' que quedo colgado: si la ultima vez que
     * se vio a esa persona fue ayer, no esta adentro ahora — se fue sin escanear la
     * salida. Sin esta comprobacion, el anti-clones la deja afuera al dia siguiente.
     */
    public boolean esDeUnDiaAnterior(Instant instante) {
        LocalDate fecha = fechaDe(instante);
        return fecha != null && fecha.isBefore(hoy());
    }
}
