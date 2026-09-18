package com.uap.control_tickets.services.biometrico;

import java.util.Map;

/**
 * Un estudiante listo para cargar al equipo (sistema → biométrico).
 *
 * @param ru        PIN que tendrá en el equipo (= RU del sistema).
 * @param nombre    nombre completo (el equipo lo trunca a 24 caracteres).
 * @param templates dedo (0-9) → template Base64 (puede ir vacío: igual se crea
 *                  el usuario, queda pendiente enrolarle la huella).
 */
public record UsuarioCarga(String ru, String nombre, Map<Integer, String> templates) {
}
