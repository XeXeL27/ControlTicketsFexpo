package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.control.ResultadoRegularizacionAccesoDto;
import com.uap.control_tickets.dto.control.ValidacionTalonarioDto;
import com.uap.control_tickets.enums.DiaFeria;
import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.enums.TipoTalonario;

/**
 * Puesto del concierto para boletos vendidos por talonario (particulares con
 * papel numerado, sin QR): se tipea el número y el puesto sabe en qué evento
 * está (EVENTO_1/2/3 o COMBO). Mismo patrón que {@link ControlBoletoService}:
 * escáner dedicado de ENTRADA o SALIDA, anti-clones por flag y día del evento.
 */
public interface ControlTalonarioService {

    /**
     * Valida el número para el evento y escáner indicados (siempre destino
     * CONCIERTO: es la puerta del concierto).
     *
     * Reglas:
     *  - Número inexistente en ese (destino, evento) → 404.
     *  - ANULADO → 409 con motivo ANULADO. El DISPONIBLE (venta sin regularizar)
     *    SÍ pasa: la rendición a veces llega después que la gente a la puerta.
     *  - Evento puntual en otro día → 409 DIA_INCORRECTO (el COMBO pasa
     *    cualquier día, vale las tres noches).
     *  - ENTRADA estando dentro → 409 YA_DENTRO; SALIDA estando fuera → 409
     *    YA_FUERA.
     * En los casos bloqueados no se registra el movimiento ni se toca la BD.
     */
    ValidacionTalonarioDto validar(Integer numero, TipoTalonario tipoEvento, TipoAcceso tipoMovimiento);

    /**
     * Regulariza UN ingreso de la puerta del concierto por número: registra
     * una ENTRADA con la fecha del día pedido (solo ADMINISTRADOR). Si el
     * boleto ya tiene una ENTRADA ese día, se rechaza ("ya tenía registro").
     * El "dentro" solo se toca si el día pedido es hoy.
     */
    ResultadoRegularizacionAccesoDto regularizarIngreso(
            Integer numero, TipoTalonario tipoEvento, DiaFeria dia);
}
