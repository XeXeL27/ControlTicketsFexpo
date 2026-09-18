package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.mapa.MapaPulsoDetalleDto;
import com.uap.control_tickets.dto.mapa.MapaPulsoDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.models.entity.MapaPulso;
import com.uap.control_tickets.models.repository.MapaPulsoDao;
import com.uap.control_tickets.services.interfaces.MapaPulsoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapaPulsoServiceImpl implements MapaPulsoService {

    /** Tope de tamaño del JSON: ~24 zonas son unos pocos KB; 256 KB es holgado. */
    private static final int MAX_CARACTERES = 256 * 1024;

    private final MapaPulsoDao mapaDao;

    @Override
    @Transactional(readOnly = true)
    public MapaPulsoDetalleDto obtener() {
        MapaPulsoDetalleDto dto = new MapaPulsoDetalleDto();
        mapaDao.findByNombre(MapaPulso.PRINCIPAL)
                .filter(m -> m.getEstado() == EstadoRegistro.ACTIVO)
                .ifPresent(m -> {
                    dto.setContenido(m.getContenido());
                    dto.setFechaModificacion(m.getFechaModificacion() != null
                            ? m.getFechaModificacion() : m.getFechaRegistro());
                    dto.setPersonalizado(true);
                });
        return dto;
    }

    @Override
    @Transactional
    public MapaPulsoDetalleDto guardar(MapaPulsoDto dto) {
        String contenido = dto.getContenido() == null ? "" : dto.getContenido().trim();
        if (contenido.isEmpty()) {
            throw new NegocioException("El acomodo del mapa no puede estar vacío");
        }
        if (contenido.length() > MAX_CARACTERES) {
            throw new NegocioException("El acomodo del mapa es demasiado grande");
        }
        // Chequeo minimo de forma: que sea un JSON y no cualquier texto. El backend
        // no interpreta el contenido, pero guardar basura dejaria la pantalla rota.
        if (!contenido.startsWith("{") && !contenido.startsWith("[")) {
            throw new NegocioException("El acomodo del mapa debe ser un JSON válido");
        }

        MapaPulso mapa = mapaDao.findByNombre(MapaPulso.PRINCIPAL).orElseGet(MapaPulso::new);
        mapa.setNombre(MapaPulso.PRINCIPAL);
        mapa.setContenido(contenido);
        mapa.setEstado(EstadoRegistro.ACTIVO); // por si se habia restaurado antes
        mapaDao.save(mapa);
        log.info("Acomodo del mapa del Pulso guardado ({} caracteres).", contenido.length());
        return obtener();
    }

    @Override
    @Transactional
    public void restaurar() {
        // Borrado fisico: el acomodo es un unico documento reemplazable y no tiene
        // valor historico. Sin fila, el frontend vuelve al acomodo del codigo.
        mapaDao.findByNombre(MapaPulso.PRINCIPAL).ifPresent(mapaDao::delete);
        log.info("Acomodo del mapa del Pulso restaurado al original.");
    }
}
