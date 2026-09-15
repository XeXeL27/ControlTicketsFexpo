package com.uap.control_tickets.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.uap.control_tickets.enums.EstadoRegistro;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * Clase base de auditoria que TODAS las entidades heredan.
 *
 * @MappedSuperclass = sus columnas se agregan a la tabla de cada entidad hija,
 *                     pero NO crea una tabla propia.
 * @EntityListeners(AuditingEntityListener) = Spring rellena solo las fechas y
 *                     el usuario que creo/modifico cada fila.
 *
 * Ademas define el campo "_estado" para el borrado logico (ACTIVO/ELIMINADO).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Getter
@Setter
public abstract class AuditoriaConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @CreatedDate
    @Column(name = "_fecha_registro", updatable = false, nullable = false)
    private Instant fechaRegistro;

    @CreatedBy
    @Column(name = "_registro_id_usuario", updatable = false)
    private Long registroIdUsuario;

    @LastModifiedDate
    @Column(name = "_fecha_modificacion")
    private Instant fechaModificacion;

    @LastModifiedBy
    @Column(name = "_modificacion_id_usuario")
    private Long modificacionIdUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "_estado", nullable = false)
    private EstadoRegistro estado = EstadoRegistro.ACTIVO;
}
