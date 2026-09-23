package com.uap.control_tickets.controllers;

import com.uap.control_tickets.dto.reporte.NominaDto;
import com.uap.control_tickets.dto.reporte.ResumenEntregasDto;
import com.uap.control_tickets.services.impl.ReporteEntregasService;
import com.uap.control_tickets.services.impl.ReporteNominaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reportes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ReporteEntregasController {

    private final ReporteEntregasService entregasService;
    private final ReporteNominaService nominaService;

    @GetMapping("/entregas")
    public ResponseEntity<ResumenEntregasDto> resumen() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entregasService.resumen());
    }

    @GetMapping("/nomina")
    public ResponseEntity<NominaDto> nomina() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(nominaService.nomina());
    }
}
