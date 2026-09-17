package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.control.BoletoDentroDto;
import com.uap.control_tickets.dto.control.ResumenBoletosDto;
import com.uap.control_tickets.dto.control.ValidacionBoletoDto;
import com.uap.control_tickets.enums.TipoAcceso;

import java.util.List;

/**
 * Validador de boletos de la feria: escaneo/tipeo del código con escáner dedicado
 * de ENTRADA o SALIDA. Registra el movimiento y rechaza intentos duplicados
 * (entrar estando dentro o salir estando fuera). Mismo patrón que
 * {@link ControlService}, sin la parte de matrícula (los boletos son anónimos).
 */
public interface ControlBoletoService {

    /**
     * Valida el código para el escáner indicado.
     *
     * Reglas:
     *  - Código inexistente → 404 (no válido).
     *  - ENTRADA estando dentro → 409 con motivo YA_DENTRO.
     *  - SALIDA estando fuera → 409 con motivo YA_FUERA.
     * En los casos bloqueados no se registra el movimiento ni se toca la BD.
     */
    ValidacionBoletoDto validar(String codigo, TipoAcceso tipoMovimiento);

    /** Boletos actualmente dentro del recinto. */
    List<BoletoDentroDto> boletosDentro();

    /** Foto del estado actual, para pintar el monitoreo al abrir la pantalla. */
    ResumenBoletosDto resumen();

    /**
     * Cierra la jornada: deja a TODOS (tickets y boletos) como fuera del recinto.
     * El sistema ya limpia solo el 'dentro' que quedo de un dia anterior cuando la
     * persona vuelve a escanear; esto es el boton manual para dejar los contadores
     * en cero al terminar el dia, sin esperar al proximo escaneo.
     *
     * @return cuantos tickets y boletos se pusieron en fuera.
     */
    int cerrarJornada();
}
