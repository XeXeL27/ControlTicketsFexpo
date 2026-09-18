package com.uap.control_tickets.enums;

/**
 * Resultado del procesamiento de UN usuario del biométrico (una fila del
 * reporte final de la sincronización). El PIN del equipo es el RU: se busca
 * con {@code EstudianteDao.findByRu}.
 *
 * CORRECTO     el RU existe en el sistema y se guardó/actualizó su template.
 * DUPLICADO    el mismo RU apareció en más de un equipo (se guarda una vez y
 *              se avisa), o su template ya estaba idéntico en otro RU.
 * NO_ENCONTRADO el PIN del equipo no es ningún RU registrado en el sistema.
 * SIN_HUELLA   el usuario existe en el equipo pero no tiene template enrolado.
 * ERROR        no se pudo leer/guardar ese usuario (mensaje en el detalle).
 * CARGADO      (SUBIDA) el RU se creó en el equipo.
 * ACTUALIZADO  (SUBIDA) el RU ya existía en el equipo y se actualizó.
 */
public enum EstadoHuellaDetalle {
    CORRECTO,
    DUPLICADO,
    NO_ENCONTRADO,
    SIN_HUELLA,
    ERROR,
    CARGADO,
    ACTUALIZADO
}
