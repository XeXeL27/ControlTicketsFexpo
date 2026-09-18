package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.dto.huella.DispositivoBiometricoDetalleDto;
import com.uap.control_tickets.dto.huella.DispositivoBiometricoDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.exception.RecursoNoEncontradoException;
import com.uap.control_tickets.models.entity.DispositivoBiometrico;
import com.uap.control_tickets.models.repository.DispositivoBiometricoDao;
import com.uap.control_tickets.services.biometrico.BiometriaException;
import com.uap.control_tickets.services.biometrico.BiometricoDriver;
import com.uap.control_tickets.services.biometrico.BiometricoDriverSelector;
import com.uap.control_tickets.services.interfaces.DispositivoBiometricoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * ABM de biométricos + prueba de conexión.
 *
 * El driver lo elige {@link BiometricoDriverSelector} (simulación, python/pyzk
 * o java TCP) según las properties {@code app.biometria.*}.
 */
@Service
@RequiredArgsConstructor
public class DispositivoBiometricoServiceImpl implements DispositivoBiometricoService {

    private final DispositivoBiometricoDao dispositivoDao;
    private final BiometricoDriverSelector selector;

    private BiometricoDriver driver() {
        return selector.actual();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispositivoBiometricoDetalleDto> listar() {
        return dispositivoDao.findAllByEstado(EstadoRegistro.ACTIVO)
                .stream().map(this::toDetalle).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DispositivoBiometricoDetalleDto obtener(Long idDispositivo) {
        return toDetalle(buscarActivo(idDispositivo));
    }

    @Override
    @Transactional
    public DispositivoBiometricoDetalleDto crear(DispositivoBiometricoDto dto) {
        DispositivoBiometrico d = new DispositivoBiometrico();
        aplicar(d, dto);
        d.setEstado(EstadoRegistro.ACTIVO);
        return toDetalle(dispositivoDao.save(d));
    }

    @Override
    @Transactional
    public DispositivoBiometricoDetalleDto actualizar(Long idDispositivo, DispositivoBiometricoDto dto) {
        DispositivoBiometrico d = buscarActivo(idDispositivo);
        aplicar(d, dto);
        return toDetalle(dispositivoDao.save(d));
    }

    @Override
    @Transactional
    public void eliminar(Long idDispositivo) {
        DispositivoBiometrico d = buscarActivo(idDispositivo);
        d.setEstado(EstadoRegistro.ELIMINADO);
        dispositivoDao.save(d);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> probarConexion(Long idDispositivo) {
        DispositivoBiometrico d = buscarActivo(idDispositivo);
        try {
            return driver().probarConexion(d);
        } catch (BiometriaException e) {
            throw new NegocioException("No se pudo conectar a " + d.getNombre() + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private void aplicar(DispositivoBiometrico d, DispositivoBiometricoDto dto) {
        if (dto.getNombre() == null || dto.getNombre().isBlank()) {
            throw new NegocioException("El nombre es obligatorio");
        }
        if (dto.getIp() == null || dto.getIp().isBlank()) {
            throw new NegocioException("La IP es obligatoria");
        }
        d.setNombre(dto.getNombre().trim());
        d.setIp(dto.getIp().trim());
        d.setPuerto(dto.getPuerto() == null ? 4370 : dto.getPuerto());
        d.setTimeoutMs(dto.getTimeoutMs() == null ? 8000 : dto.getTimeoutMs());
        d.setActivo(dto.getActivo() == null || dto.getActivo());
        // Clave: en blanco al editar conserva la guardada (al crear queda sin clave).
        if (dto.getClaveComunicacion() != null && !dto.getClaveComunicacion().isBlank()) {
            String clave = dto.getClaveComunicacion().trim();
            if (!clave.chars().allMatch(Character::isDigit)) {
                throw new NegocioException("La clave de comunicación debe ser solo dígitos");
            }
            if (clave.length() > 10) {
                throw new NegocioException("La clave de comunicación es muy larga (máx. 10 dígitos)");
            }
            d.setClaveComunicacion(clave);
        }
    }

    private DispositivoBiometrico buscarActivo(Long id) {
        return dispositivoDao.findById(id)
                .filter(e -> e.getEstado() == EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Biométrico no encontrado"));
    }

    private DispositivoBiometricoDetalleDto toDetalle(DispositivoBiometrico d) {
        DispositivoBiometricoDetalleDto dto = new DispositivoBiometricoDetalleDto();
        dto.setIdDispositivo(d.getIdDispositivo());
        dto.setNombre(d.getNombre());
        dto.setIp(d.getIp());
        dto.setPuerto(d.getPuerto());
        dto.setTimeoutMs(d.getTimeoutMs());
        dto.setActivo(d.getActivo());
        dto.setTieneClave(d.getClaveComunicacion() != null && !d.getClaveComunicacion().isBlank());
        dto.setEstado(d.getEstado().name());
        return dto;
    }
}
