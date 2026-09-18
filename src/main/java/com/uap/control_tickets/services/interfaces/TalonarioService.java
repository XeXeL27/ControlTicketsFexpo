package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.talonario.*;
import com.uap.control_tickets.enums.DestinoTalonario;
import com.uap.control_tickets.enums.TipoTalonario;

import java.util.List;

/** Control de VENTA de boletos por talonario. No tiene nada que ver con el ingreso. */
public interface TalonarioService {

    /** Talonarios con su avance de ventas. Si soloMios, acota a los del usuario logueado. */
    List<TalonarioDetalleDto> listar(DestinoTalonario destino, TipoTalonario tipo, boolean soloMios);

    /**
     * Deja los talonarios de una vendedora exactamente como dice la lista (pueden
     * ser de varios destinos y eventos a la vez).
     */
    ResultadoAsignacionDto asignar(AsignacionTalonariosDto dto);

    TalonarioDetalleDto obtener(Long idTalonario);

    /** Alta de un talonario; genera sus boletos y valida que el rango no se solape. */
    TalonarioDetalleDto crear(TalonarioDto dto);

    /** Alta de varios talonarios correlativos del mismo tipo, sin huecos. */
    List<TalonarioDetalleDto> generar(GeneracionTalonariosDto dto);

    /** Edita nombre, precio y vendedora asignada. El tipo y el rango no se tocan. */
    TalonarioDetalleDto actualizar(Long idTalonario, TalonarioActualizarDto dto);

    void eliminar(Long idTalonario);

    /** Boletos de un talonario, para la pantalla de ventas. */
    List<BoletoTalonarioDto> boletos(Long idTalonario);

    /** Marca boletos como vendidos/anulados/disponibles (por rango, hasta N, o sueltos). */
    ResultadoMarcadoDto marcar(MarcarVentaDto dto);
}
