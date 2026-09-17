package com.uap.control_tickets.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Habilita los métodos asíncronos (@Async) del sistema.
 *
 * El módulo de huellas (sincronización con biométricos ZKTeco) corre en este
 * pool aparte para no bloquear los hilos de Tomcat mientras recorre los
 * usuarios de cada equipo: con miles de RUs la tarea tarda varios minutos y
 * el frontend va consultando el progreso por polling/WebSocket.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Pool dedicado a las sincronizaciones de huellas. Un solo hilo con cola:
     * las sincronizaciones corren de a una (los equipos no se llevan bien con
     * conexiones concurrentes desde el mismo origen y así el progreso es lineal).
     */
    @Bean(name = "huellasExecutor")
    public Executor huellasExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("huellas-");
        executor.initialize();
        return executor;
    }
}
