package com.uap.control_tickets.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Donde se guardan las fotos del registro de salida (ver 8.2.1 del CLAUDE.md).
 *
 * Es una CARPETA del servidor, no la base de datos: en la BD queda solo el nombre
 * del archivo. Por defecto cuelga del directorio desde donde se arranca la app,
 * que en local es la raiz del proyecto.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.fotos")
public class FotosProperties {

    /** Carpeta donde se escriben los .jpg. Relativa al directorio de trabajo, o absoluta. */
    private String directorio = "fotos-salida";
}
