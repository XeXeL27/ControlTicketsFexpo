package com.uap.control_tickets.apivalidacaion.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstudianteDto {

    private String vigencia;
    private int ru;
    private int periodo;
    private String ci;
    private String categoria;


    private LocalDate fecha_nacimiento;

    private String direccion;
    private int gestion;
    private String nombres;
    private String nacionalidad;

    @JsonProperty("estado_matriculacion")
    private boolean estadoMatriculacion;

    private String url_imagen;
    private String correo;
    private int periodo_estudiante;
    private String apellido_paterno;
    private String apellido_materno;
    private String celular;
    private String carrera;
    private String sexo;
    private String plan;
    private String facultad;
    private String tipo_carrera;
}