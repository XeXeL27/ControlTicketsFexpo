package com.uap.control_tickets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Punto de arranque de la aplicacion.
 *
 * @SpringBootApplication  -> activa autoconfiguracion, escaneo de componentes
 *                            y arranque del servidor embebido (Tomcat).
 * @EnableJpaAuditing      -> habilita el llenado automatico de los campos de
 *                            auditoria (_fecha_registro, _registro_id_usuario,
 *                            etc.) definidos en AuditoriaConfig. El "auditorAware"
 *                            dice QUIEN es el usuario actual (ver AuditorAwareImpl).
 */
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class ControlTicketsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ControlTicketsApplication.class, args);
	}

}
