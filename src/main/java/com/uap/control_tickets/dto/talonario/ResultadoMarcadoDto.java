package com.uap.control_tickets.dto.talonario;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Que paso al marcar: cuantos cambiaron, cuantos ya estaban asi, y los avisos. */
@Getter
@Setter
public class ResultadoMarcadoDto {
    private int solicitados;
    private int cambiados;
    /** Ya estaban en ese estado: no es un error, pero conviene informarlo. */
    private int sinCambios;
    private List<String> avisos = new ArrayList<>();

    public void aviso(String texto) {
        avisos.add(texto);
    }
}
