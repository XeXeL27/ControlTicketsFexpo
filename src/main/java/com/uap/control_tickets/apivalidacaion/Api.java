package com.uap.control_tickets.apivalidacaion;

import com.uap.control_tickets.apivalidacaion.Dto.ApiResponseDto;
import com.uap.control_tickets.apivalidacaion.Service.ApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/validacion")
@RequiredArgsConstructor
public class Api {
    private final ApiService apiService;


    @GetMapping("/informacion/{ru}")
    public ResponseEntity<?> obtenerInformacion(@PathVariable Integer ru) {
        ApiResponseDto respuesta = apiService.informacion(ru);

        if (respuesta == null || !respuesta.isOk() || respuesta.getData() == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Error al consultar la API: " + (respuesta != null ? respuesta.getMensaje() : "Sin respuesta"));
        }

        if (!respuesta.getData().isEstadoMatriculacion()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("El estudiante con RU " + ru + " no está matriculado.");
        }

        return ResponseEntity.ok(respuesta);
    }
}