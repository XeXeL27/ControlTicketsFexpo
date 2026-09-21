package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.apivalidacaion.Dto.ApiResponseDto;
import com.uap.control_tickets.dto.control.DetalleIngresoConciertoDto;
import com.uap.control_tickets.dto.control.PersonaDentroDto;
import com.uap.control_tickets.dto.control.ReporteIngresosConciertoDto;
import com.uap.control_tickets.dto.control.ReporteIngresosEstudiantesDto;
import com.uap.control_tickets.dto.control.ResultadoRegularizacionAccesoDto;
import com.uap.control_tickets.dto.control.ValidacionTicketDto;
import com.uap.control_tickets.enums.CategoriaTicket;
import com.uap.control_tickets.enums.DiaFeria;
import com.uap.control_tickets.enums.TipoAcceso;

import java.util.List;

/**
 * Validador de acceso: escaneo del QR del ticket con escaner dedicado de
 * ENTRADA o SALIDA. Registra el movimiento, valida la matricula para
 * estudiantes (solo al entrar) y rechaza intentos duplicados (entrar estando
 * dentro o salir estando fuera). No guarda datos de la consulta en BD.
 */
public interface ControlService {

    /**
     * Valida el codigo escaneado (qr_token) para el escaner indicado.
     *
     * Reglas:
     *  - ENTRADA estando dentro → 409 con motivo YA_DENTRO (ya entro).
     *  - SALIDA estando fuera → 409 con motivo YA_FUERA (no entro).
     *  - ENTRADA de estudiante no matriculado → 409 con motivo NO_MATRICULADO.
     * En ninguno de esos casos se registra el movimiento ni se toca la BD.
     */
    ValidacionTicketDto validar(String codigo, TipoAcceso tipoMovimiento);

    /** Personas que estan actualmente dentro del recinto (dentro=true). */
    List<PersonaDentroDto> personasDentro();

    /**
     * Ingresos (solo ENTRADAS) al concierto de los 3 días del evento, por
     * categoría (estudiantes, administrativos, docentes, particulares).
     * Base del apartado "Reportes" y de su PDF.
     */
    ReporteIngresosConciertoDto reporteIngresosPorDia();

    /**
     * Detalle nominal de ingresos: qué tickets registraron ENTRADA en el día
     * pedido (o en los 3 días si dia es null), opcionalmente de una sola
     * categoría. Una fila por ticket con sus entradas y su última entrada.
     */
    List<DetalleIngresoConciertoDto> detalleIngresos(DiaFeria dia, CategoriaTicket categoria);

    /**
     * ENTRADAS de ESTUDIANTE agrupadas por carrera, en el día pedido (o en los
     * 3 días si dia es null). Una fila por carrera con tickets distintos y suma
     * de ENTRADAS, más los totales. Base del reporte "Estudiantes por carrera".
     */
    ReporteIngresosEstudiantesDto reporteEstudiantesPorCarrera(DiaFeria dia);

    /**
     * Regulariza UN ingreso QR: registra una ENTRADA con la fecha del día
     * pedido (solo ADMINISTRADOR). Si el ticket ya tiene una ENTRADA ese día,
     * se rechaza ("ya tenía registro"). El "dentro" solo se toca si el día
     * pedido es hoy.
     */
    ResultadoRegularizacionAccesoDto regularizarIngreso(String codigo, DiaFeria dia);

    /**
     * Consulta puntual de matricula por RU (sin tocar la BD).
     * Devuelve la respuesta completa (con o sin matricula).
     */
    ApiResponseDto consultarSigse(Integer ru);
}
