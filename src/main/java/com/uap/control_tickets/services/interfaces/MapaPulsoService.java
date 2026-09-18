package com.uap.control_tickets.services.interfaces;

import com.uap.control_tickets.dto.mapa.MapaPulsoDetalleDto;
import com.uap.control_tickets.dto.mapa.MapaPulsoDto;

/** Acomodo del mapa 3D del Pulso FEXPO. */
public interface MapaPulsoService {

    /** El acomodo guardado; personalizado=false si nunca se editó. */
    MapaPulsoDetalleDto obtener();

    /** Guarda (o reemplaza) el acomodo. */
    MapaPulsoDetalleDto guardar(MapaPulsoDto dto);

    /** Borra el acomodo guardado: el mapa vuelve al original del código. */
    void restaurar();
}
