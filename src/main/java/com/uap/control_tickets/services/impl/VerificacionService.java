package com.uap.control_tickets.services.impl;

import com.uap.control_tickets.Utils.csv.CsvUtils;
import com.uap.control_tickets.dto.verificacion.ResultadoVerificacionDto;
import com.uap.control_tickets.dto.verificacion.VerificacionCodigoDto;
import com.uap.control_tickets.enums.EstadoRegistro;
import com.uap.control_tickets.exception.NegocioException;
import com.uap.control_tickets.models.repository.AdministrativoDao;
import com.uap.control_tickets.models.repository.DocenteDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VerificacionService {

    private final AdministrativoDao administrativoDao;
    private final DocenteDao docenteDao;

    @Transactional(readOnly = true)
    public ResultadoVerificacionDto verificarCsv(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("El archivo CSV está vacío");
        }
        String contenido;
        try {
            contenido = CsvUtils.decodificar(archivo.getBytes());
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }

        ResultadoVerificacionDto resultado = new ResultadoVerificacionDto();
        // Para evitar contar duplicados del mismo CSV como faltantes múltiples veces si se desea, mantenemos el orden pero avisamos
        Set<String> vistos = new LinkedHashSet<>();

        try (BufferedReader br = new BufferedReader(new StringReader(contenido))) {
            String linea;
            int fila = 0;
            char sep = ',';
            boolean primera = true;
            while ((linea = br.readLine()) != null) {
                if (primera) {
                    linea = CsvUtils.quitarBom(linea);
                    sep = CsvUtils.detectarSeparador(linea);
                    primera = false;
                    if (esEncabezado(linea, sep)) continue;
                }
                if (linea.isBlank()) continue;
                String[] c = CsvUtils.separar(linea, sep);
                String codigo = CsvUtils.get(c, 0);
                if (codigo == null) codigo = "";
                codigo = codigo.trim();
                if (codigo.isEmpty()) continue;
                // Evitar duplicados en el archivo
                if (!vistos.add(codigo)) continue;
                fila++;
                resultado.setTotalFilas(resultado.getTotalFilas() + 1);
                VerificacionCodigoDto dto = verificarUno(codigo);
                dto.setFila(fila);
                dto.setCodigo(codigo);
                resultado.agregar(dto);
            }
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el CSV: " + e.getMessage());
        }
        return resultado;
    }

    private VerificacionCodigoDto verificarUno(String codigo) {
        var admOpt = administrativoDao.findByCodigoAdministrativo(codigo)
                .filter(a -> a.getEstado() == EstadoRegistro.ACTIVO);
        if (admOpt.isPresent()) {
            var adm = admOpt.get();
            var p = adm.getPersona();
            return new VerificacionCodigoDto(codigo, true, "ADMINISTRATIVO", p.getNombreCompleto(), p.getCi(), null);
        }
        var docOpt = docenteDao.findByCodigoDocente(codigo)
                .filter(d -> d.getEstado() == EstadoRegistro.ACTIVO);
        if (docOpt.isPresent()) {
            var doc = docOpt.get();
            var p = doc.getPersona();
            return new VerificacionCodigoDto(codigo, true, "DOCENTE", p.getNombreCompleto(), p.getCi(), null);
        }
        return new VerificacionCodigoDto(codigo, false, "NO_REGISTRADO", null, null, null);
    }

    private boolean esEncabezado(String linea, char sep) {
        String[] c = CsvUtils.separar(linea, sep);
        if (c.length == 0) return false;
        String primera = CsvUtils.normalizar(c[0]);
        if (primera.contains("codigo") || primera.equals("cod") || primera.equals("codigoadm") || primera.equals("codigodocente") || primera.equals("item") || primera.equals("nro") || primera.equals("n")) return true;
        // si la celda no tiene dígitos ni letras de código típico (solo rótulo)
        String codigo = CsvUtils.get(c, 0);
        if (codigo != null && codigo.chars().noneMatch(Character::isLetterOrDigit)) return true;
        return false;
    }
}
