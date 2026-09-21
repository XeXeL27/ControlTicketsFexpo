package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.control.BoletoDentroDto;
import com.uap.control_tickets.dto.control.ReporteIngresosFeriaDto;
import com.uap.control_tickets.dto.control.ResumenBoletosDto;
import com.uap.control_tickets.dto.control.ResultadoRegularizacionAccesoDto;
import com.uap.control_tickets.dto.control.ValidacionBoletoDto;
import com.uap.control_tickets.enums.DiaFeria;
import com.uap.control_tickets.enums.TipoAcceso;
import com.uap.control_tickets.enums.TipoBoleto;

import java.util.List;

/**
 * Validador de boletos: escaneo/tipeo del código con escáner dedicado
 * de ENTRADA o SALIDA, DENTRO de un tipo de boleto (FERIA o PARQUEO). Registra
 * el movimiento y rechaza intentos duplicados (entrar estando dentro o salir
 * estando fuera). Mismo patrón que {@link ControlService}, sin la parte de
 * matrícula (los boletos son anónimos).
 */
public interface ControlBoletoService {

    /**
     * Valida el código para el escáner indicado, dentro del tipo indicado.
     *
     * Reglas:
     *  - Código inexistente EN ESE TIPO → 404 (no válido). El mismo código
     *    puede existir en el otro tipo y es otro boleto.
     *  - ENTRADA estando dentro → 409 con motivo YA_DENTRO.
     *  - SALIDA estando fuera → 409 con motivo YA_FUERA.
     * En los casos bloqueados no se registra el movimiento ni se toca la BD.
     */
    ValidacionBoletoDto validar(String codigo, TipoAcceso tipoMovimiento, TipoBoleto tipoBoleto);

    /** Boletos actualmente dentro del recinto. */
    List<BoletoDentroDto> boletosDentro();

    /** Foto del estado actual, para pintar el monitoreo al abrir la pantalla. */
    ResumenBoletosDto resumen();

    /**
     * Regulariza UN ingreso de feria/parqueo: registra una ENTRADA con la
     * fecha del día pedido (solo ADMINISTRADOR). Si el boleto ya tiene una
     * ENTRADA ese día, se rechaza ("ya tenía registro"). El "dentro" solo se
     * toca si el día pedido es hoy.
     */
    ResultadoRegularizacionAccesoDto regularizarIngreso(
            String codigo, TipoBoleto tipoBoleto, DiaFeria dia);

    /**
     * Ingresos (solo ENTRADAS) de los 3 días de la feria, separados en
     * FERIA y PARQUEO. Base del apartado "Reportes" y de su PDF.
     */
    ReporteIngresosFeriaDto reporteIngresosPorDia();

    /**
     * Cierra la jornada: deja a TODOS (tickets y boletos) como fuera del recinto.
     * El sistema ya limpia solo el 'dentro' que quedo de un dia anterior cuando la
     * persona vuelve a escanear; esto es el boton manual para dejar los contadores
     * en cero al terminar el dia, sin esperar al proximo escaneo.
     *
     * @return cuantos tickets y boletos se pusieron en fuera.
     */
    int cerrarJornada();

    /**
     * Guarda los datos que dio el visitante al salir diciendo que va a volver.
     * Los tres campos son opcionales; sin ninguno queda `sinDatos=true`.
     */
    com.uap.control_tickets.dto.control.RegistroSalidaDetalleDto registrarSalida(
            com.uap.control_tickets.dto.control.RegistroSalidaDto dto);

    /**
     * Bytes (JPEG) de la foto de un registro de salida, o null si no tiene.
     * La imagen se guarda en una carpeta del disco, no en la BD.
     */
    byte[] fotoDeRegistro(Long idRegistro);
}
