package com.uap.control_tickets.services.biometrico;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Scripts Python (pyzk) que viajan dentro del jar (resources/biometria).
 *
 * Al arrancar se extraen a un directorio temporal, porque un proceso externo
 * no puede ejecutarlos desde dentro del jar. Así funciona igual en dev
 * (./mvnw) y en producción (java -jar).
 *
 * Requisito del servidor: Python 3 + {@code pip install pyzk}.
 */
@Component
public class BiometriaScripts {

    private static final List<String> SCRIPTS = List.of("zk_probar.py", "zk_descargar.py", "zk_cargar.py");

    private Path dir;

    @PostConstruct
    public void extraer() throws IOException {
        dir = Files.createTempDirectory("biometria-scripts");
        for (String nombre : SCRIPTS) {
            try (InputStream in = getClass().getResourceAsStream("/biometria/" + nombre)) {
                if (in == null) {
                    throw new IllegalStateException("No se encontró el script /biometria/" + nombre);
                }
                Path destino = dir.resolve(nombre);
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
                destino.toFile().deleteOnExit();
            }
        }
        dir.toFile().deleteOnExit();
    }

    public Path script(String nombre) {
        return dir.resolve(nombre);
    }
}
