package com.uap.control_tickets.Utils.ticket;

/**
 * Datos que se imprimen sobre la plantilla del ticket de estudiante.
 *
 * @param nombreCompleto  va en el recuadro "NOMBRE COMPLETO"
 * @param ru              va en el recuadro "R.U."
 * @param carrera         va en el recuadro "CARRERA"
 * @param codigo          va en el recuadro "CODIGO TICKETS" (codigoIdentificacion)
 * @param qrContenido     texto que codifica el QR (normalmente el qrToken)
 */
public record DatosTicketEstudiante(
        String nombreCompleto,
        String ru,
        String carrera,
        String codigo,
        String qrContenido
) {}
