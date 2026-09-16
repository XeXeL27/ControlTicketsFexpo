package com.uap.control_tickets.dto.control;

import java.time.Instant;
import java.util.List;

public record HistorialPersonaDto(List<Movimiento> movimientos, long total, int pagina, int paginas) {
    public record Movimiento(Long idAcceso, String tipo, Instant fechaHora,
                             String codigoTicket, String categoria) {}
}
