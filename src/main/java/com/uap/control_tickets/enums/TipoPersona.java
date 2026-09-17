package com.uap.control_tickets.enums;

/**
 * De donde "viene" una persona, para poder filtrarlas en la pantalla.
 *
 * Una persona puede tener mas de un vinculo (un docente que ademas es usuario del
 * sistema); se muestra el primero de esta lista que aplique, que es el orden en que
 * a la gente le interesa clasificarlas.
 */
public enum TipoPersona {
    ESTUDIANTE,
    ADMINISTRATIVO,
    DOCENTE,
    /** Creada solo para darle un usuario del sistema (y asignarle roles). */
    USUARIO,
    /** Cargada a mano y todavia sin vinculo con nada. */
    SIN_VINCULO
}
