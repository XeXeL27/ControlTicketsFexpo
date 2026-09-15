package com.uap.control_tickets.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Agrega el prefijo "/api" a TODOS los @RestController automaticamente.
 * Asi los controllers se escriben con rutas cortas (ej. @RequestMapping("/usuarios"))
 * pero el cliente las llama como /api/usuarios. Mantiene limpio el codigo.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",
                HandlerTypePredicate.forAnnotation(RestController.class));
    }
}
