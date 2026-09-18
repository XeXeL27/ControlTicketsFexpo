package com.uap.control_tickets.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Agrega el prefijo "/api" a TODOS los @RestController automaticamente.
 * Asi los controllers se escriben con rutas cortas (ej. @RequestMapping("/usuarios"))
 * pero el cliente las llama como /api/usuarios. Mantiene limpio el codigo.
 *
 * Ademas sirve el frontend empaquetado: cuando el jar se arma con el perfil
 * maven "produccion", lo compilado de Vue (frontend/dist) queda en /static y
 * este handler lo sirve. Como el router de Vue usa historial (createWebHistory,
 * ej. /estudiantes, /login), cualquier ruta que no sea un archivo real ni la API
 * se responde con index.html para que el frontend resuelva la vista.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api",
                HandlerTypePredicate.forAnnotation(RestController.class));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        // Archivo real (/, /index.html, /assets/..., /logo.png): servirlo.
                        Resource pedido = location.createRelative(resourcePath);
                        if (pedido.exists() && pedido.isReadable()) {
                            return pedido;
                        }
                        // La API, el WebSocket y Swagger NO son del frontend: se dejan pasar
                        // (siguen su curso normal; si no existen dan su 404/403 de siempre,
                        // no el index.html).
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("ws/")
                                || resourcePath.startsWith("v3/")
                                || resourcePath.startsWith("swagger-ui")) {
                            return null;
                        }
                        // Ruta del router de Vue (/login, /estudiantes...): el index.html.
                        return location.createRelative("index.html");
                    }
                });
    }
}
