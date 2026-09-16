package com.uap.control_tickets.controllers.control;

import com.uap.control_tickets.dto.control.HistorialPersonaDto;
import com.uap.control_tickets.dto.control.ReportePersonaDto;
import com.uap.control_tickets.services.impl.ReporteAccesoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/control/reportes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CONTROL')")
public class ReporteAccesoController {
    private final ReporteAccesoService service;

    @GetMapping("/personas")
    public ResponseEntity<List<ReportePersonaDto>> listar() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listar());
    }

    @GetMapping("/personas/{idPersona}/historial")
    public ResponseEntity<HistorialPersonaDto> historial(@PathVariable Long idPersona,
                                                       @RequestParam(defaultValue = "0") int pagina) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.historial(idPersona, pagina));
    }
}
