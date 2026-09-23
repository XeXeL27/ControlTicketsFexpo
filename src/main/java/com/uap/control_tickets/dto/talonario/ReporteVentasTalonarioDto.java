package com.uap.control_tickets.dto.talonario;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reporte de todos los talonarios vendidos: avance por talonario, ventas por
 * vendedora y totales del evento. Base del apartado "Reportes" y de su PDF.
 */
@Data
public class ReporteVentasTalonarioDto {
    /** Un elemento por talonario activo, con su avance (vendidos, monto, ...). */
    private List<TalonarioDetalleDto> talonarios;
    /** Ventas agrupadas por responsable, de mayor a menor. */
    private List<VentaVendedoraDto> porVendedora;
    private int totalTalonarios;
    private long totalBoletos;
    private long totalVendidos;
    private long totalDisponibles;
    private long totalAnulados;
    /** Suma de montos de talonarios con precio. null si ninguno tiene precio. */
    private BigDecimal totalMontoVendido;

    /** Tickets entregados de adm/doc como boletos vendidos (precio 125 c/u). */
    private long totalEntregadosAdm;
    private long totalEntregadosDoc;
    private long totalEntregadosAdmDoc;
    private BigDecimal montoEntregadosAdmDoc;
    /** Precio fijo de cada entrada entregada adm/doc. */
    private BigDecimal precioEntregadoAdmDoc;
}
