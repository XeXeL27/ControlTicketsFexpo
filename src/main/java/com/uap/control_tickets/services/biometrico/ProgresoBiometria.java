package com.uap.control_tickets.services.biometrico;

/**
 * El driver la llama a medida que avanza la descarga: primero una vez con la
 * lista completa (para fijar el total de la barra) y después una vez por cada
 * usuario ya con sus templates.
 */
public interface ProgresoBiometria {

    /** Se descubrió cuántos usuarios trae el equipo (antes de bajar templates). */
    default void listaDescubierta(String equipo, int totalEquipo) {
    }

    /** Un usuario terminó de descargarse (con o sin templates). */
    default void usuarioListo(String equipo, UsuarioBiometrico usuario, int procesadosEquipo, int totalEquipo) {
    }
}
