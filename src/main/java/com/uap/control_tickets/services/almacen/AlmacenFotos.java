package com.uap.control_tickets.services.almacen;

import com.uap.control_tickets.config.FotosProperties;
import com.uap.control_tickets.exception.NegocioException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * Guarda y lee las fotos del registro de salida en una CARPETA del disco.
 *
 * En la base de datos queda solo la ruta relativa (ej. "2026-09-18/a1b2....jpg");
 * los bytes viven en el archivo. Se ordena por fecha en subcarpetas para que
 * borrar lo de un dia sea borrar una carpeta, y para que ningun directorio junte
 * miles de archivos.
 *
 * El nombre del archivo lo inventa SIEMPRE el servidor (UUID). Nunca se arma con
 * datos del cliente: un nombre como "../../application.properties" permitiria
 * escribir o leer fuera de la carpeta. Aun asi, al leer se vuelve a comprobar que
 * la ruta caiga dentro de la base, porque el valor viaja por la BD y esa
 * comprobacion es la unica que protege si alguna vez se escribe a mano.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlmacenFotos {

    private final FotosProperties propiedades;

    /** Carpeta base ya resuelta a ruta absoluta y normalizada. */
    private Path base;

    @PostConstruct
    void prepararCarpeta() {
        base = Path.of(propiedades.getDirectorio()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(base);
            log.info("Fotos del registro de salida en: {}", base);
        } catch (IOException e) {
            // Se avisa al arrancar y no cuando el primer visitante da su foto.
            throw new IllegalStateException("No se pudo crear la carpeta de fotos: " + base, e);
        }
    }

    /**
     * Escribe la foto que mando el navegador (data URI en base64) y devuelve la
     * ruta relativa que hay que guardar en la BD. Null si no vino ninguna foto.
     */
    public String guardar(String dataUri) {
        if (dataUri == null || dataUri.isBlank()) return null;

        byte[] bytes = decodificar(dataUri);
        String relativa = LocalDate.now() + "/" + UUID.randomUUID() + ".jpg";
        Path destino = base.resolve(relativa);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, bytes);
        } catch (IOException e) {
            log.error("No se pudo escribir la foto en {}", destino, e);
            throw new NegocioException("No se pudo guardar la foto. Intente de nuevo.");
        }
        return relativa;
    }

    /** Bytes del archivo, o null si la ruta es nula o el archivo ya no esta. */
    public byte[] leer(String relativa) {
        if (relativa == null || relativa.isBlank()) return null;
        Path archivo = base.resolve(relativa).normalize();
        // Cinturon y tirantes: la ruta tiene que seguir cayendo dentro de la base.
        if (!archivo.startsWith(base)) {
            log.warn("Ruta de foto fuera de la carpeta base: {}", relativa);
            return null;
        }
        try {
            return Files.exists(archivo) ? Files.readAllBytes(archivo) : null;
        } catch (IOException e) {
            log.error("No se pudo leer la foto {}", archivo, e);
            return null;
        }
    }

    /** Borra el archivo si existe. No falla si ya no esta. */
    public void borrar(String relativa) {
        if (relativa == null || relativa.isBlank()) return;
        Path archivo = base.resolve(relativa).normalize();
        if (!archivo.startsWith(base)) return;
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            log.warn("No se pudo borrar la foto {}", archivo, e);
        }
    }

    /** Saca el "data:image/jpeg;base64," del principio y decodifica. */
    private byte[] decodificar(String dataUri) {
        int coma = dataUri.indexOf(',');
        String base64 = coma >= 0 ? dataUri.substring(coma + 1) : dataUri;
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new NegocioException("La foto llegó con un formato que no se pudo leer.");
        }
    }
}
