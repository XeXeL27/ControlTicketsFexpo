package com.uap.control_tickets.dto.control;

import com.uap.control_tickets.enums.DiaFeria;
import com.uap.control_tickets.enums.TipoBoleto;
import com.uap.control_tickets.enums.TipoTalonario;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Regulariza UN ingreso: registra una ENTRADA con la fecha del día pedido.
 *
 * Cada puerta usa sus campos y los demás se ignoran:
 *  - Concierto QR: codigo (qrToken o código de identificación) + dia.
 *  - Concierto talonario: numero + tipoEvento + dia.
 *  - Feria/parqueo: codigo + tipoBoleto + dia.
 *
 * Es una corrección del admin (escaneo que no se hizo en puerta): no valida
 * día del boleto/evento ni matrícula, y el "dentro" solo se toca si el día
 * pedido es hoy. Si ese código/número ya tiene una ENTRADA ese día, se
 * rechaza ("ya tenía registro") en vez de duplicar.
 */
@Data
public class RegularizacionAccesoDto {

    /** Código del ticket QR o del boleto de feria/parqueo. */
    private String codigo;

    /** Número del boleto de talonario (puerta del concierto por número). */
    private Integer numero;

    /** Bolsa del boleto (FERIA o PARQUEO). Solo puerta feria/parqueo. */
    private TipoBoleto tipoBoleto;

    /** Evento del talonario. Solo puerta del concierto por número. */
    private TipoTalonario tipoEvento;

    /** Día al que se imputa el ingreso. */
    @NotNull(message = "Indique el día del ingreso")
    private DiaFeria dia;
}
