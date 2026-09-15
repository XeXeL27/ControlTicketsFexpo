package com.uap.control_tickets.Utils.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.uap.control_tickets.exception.NegocioException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generador de codigos QR (usa la libreria ZXing).
 *
 * Convierte un texto (por ejemplo el qrToken de un ticket) en la imagen de un
 * codigo QR. Devuelve:
 *  - los bytes PNG listos para enviar por HTTP o guardar, y
 *  - la imagen en memoria (BufferedImage) para incrustarla en el ticket/PDF.
 *
 * Nivel de correccion de errores M (~15%): el QR se sigue leyendo aunque una
 * parte quede manchada o impresa con baja calidad.
 */
@Component
public class QrGenerator {

    private static final int TAMANO_POR_DEFECTO = 300; // px (cuadrado)
    private static final int MARGEN = 1;               // modulos de borde blanco

    /** Genera el QR como imagen en memoria (para incrustar en el ticket). */
    public BufferedImage generarImagen(String contenido, int tamanoPx) {
        if (contenido == null || contenido.isBlank()) {
            throw new NegocioException("El contenido del QR no puede estar vacio");
        }
        int tamano = tamanoPx > 0 ? tamanoPx : TAMANO_POR_DEFECTO;

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, MARGEN);

        try {
            BitMatrix matriz = new QRCodeWriter()
                    .encode(contenido, BarcodeFormat.QR_CODE, tamano, tamano, hints);
            return MatrixToImageWriter.toBufferedImage(matriz);
        } catch (WriterException e) {
            throw new NegocioException("No se pudo generar el codigo QR: " + e.getMessage());
        }
    }

    /** Genera el QR como bytes PNG (para responder por HTTP o guardar en disco). */
    public byte[] generarPng(String contenido, int tamanoPx) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(generarImagen(contenido, tamanoPx), "PNG", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new NegocioException("No se pudo convertir el QR a PNG: " + e.getMessage());
        }
    }

    /** Variante con el tamano por defecto. */
    public byte[] generarPng(String contenido) {
        return generarPng(contenido, TAMANO_POR_DEFECTO);
    }
}
