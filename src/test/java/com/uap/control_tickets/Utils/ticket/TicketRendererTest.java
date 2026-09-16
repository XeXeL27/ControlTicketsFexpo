package com.uap.control_tickets.Utils.ticket;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.uap.control_tickets.Utils.qr.QrGenerator;
import com.uap.control_tickets.enums.FormatoPliego;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;

import java.awt.Color;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketRendererTest {
    private final DatosTicketDocente docente = new DatosTicketDocente(
            "María Pérez López", "1234567", "42", "Ingeniería de Sistemas", "DOC-000001",
            "3c481eaf-e67b-4141-a810-12a405989ee0");

    @Test
    void docenteConQrLegibleYPliegosDeAmbosFormatos() throws Exception {
        for (FormatoPliego formato : new FormatoPliego[]{FormatoPliego.MIXTO_8, FormatoPliego.HORIZONTAL_5}) {
            var imagen = renderer.renderDocente(docente, formato);
            var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen)));
            assertEquals(docente.qrContenido(), new MultiFormatReader().decode(bitmap).getText());
            assertEquals(Color.WHITE.getRGB(), imagen.getRGB(0, 0));
            try (PdfReader reader = new PdfReader(renderer.pdfPliegoDocentes(
                    Collections.nCopies(formato.getPorHoja() + 1, docente), formato))) {
                assertEquals(2, reader.getNumberOfPages());
                assertEquals(21.5 * 72 / 2.54, reader.getPageSize(1).getWidth(), 0.02);
                assertEquals(33 * 72 / 2.54, reader.getPageSize(1).getHeight(), 0.02);
            }
        }
    }

    @Test
    void pdfIndividualDocenteRespetaMedidasReales() throws Exception {
        try (PdfReader reader = new PdfReader(renderer.pdfDocente(docente))) {
            assertEquals(1, reader.getNumberOfPages());
            assertEquals(15.54 * 72 / 2.54, reader.getPageSize(1).getWidth(), 0.02);
            assertEquals(5.16 * 72 / 2.54, reader.getPageSize(1).getHeight(), 0.02);
        }
    }
    private final TicketRenderer renderer = new TicketRenderer(new QrGenerator());
    private final DatosTicketAdministrativo datos = new DatosTicketAdministrativo(
            "María Pérez López", "1234567", "ADM-42", "3c481eaf-e67b-4141-a810-12a405989ee0");

    @Test
    void reversoBlancoConQrLegibleEnAmbosFormatos() throws Exception {
        for (FormatoPliego formato : FormatoPliego.values()) {
            var imagen = renderer.renderAdministrativo(datos, formato);
            assertEquals(Math.round(20.8 / 2.54 * 300), imagen.getWidth());
            assertEquals(Math.round(7.42 / 2.54 * 300), imagen.getHeight());
            assertEquals(Color.WHITE.getRGB(), imagen.getRGB(0, 0));
            var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen)));
            assertEquals(datos.qrContenido(), new MultiFormatReader().decode(bitmap).getText());
        }
    }

    @Test
    void administrativoTienePrincipalConQrFranjaVaciaYtalonSinQr() throws Exception {
        var imagen = renderer.renderAdministrativo(datos, FormatoPliego.ADMINISTRATIVO_5);
        int anchoPrincipal = (int) Math.round(11.8 / 2.54 * 300);
        int inicioPrincipal = imagen.getWidth() - anchoPrincipal;
        int altoBloque = (int) Math.round(3.2 / 2.54 * 300);
        int inicioBloque = (imagen.getHeight() - altoBloque) / 2;
        for (int x = inicioPrincipal; x < imagen.getWidth(); x++) {
            for (int y = 0; y < imagen.getHeight(); y++) {
                if (y < inicioBloque || y >= inicioBloque + altoBloque) {
                    assertEquals(Color.WHITE.getRGB(), imagen.getRGB(x, y));
                }
            }
        }
        int anchoTalon = (int) Math.round(9 / 2.54 * 300);
        int inicioTalon = 0;
        // Reservar los primeros 2 cm del talón completamente libres para la grampa.
        for (int x = 0; x < (int) Math.round(2 / 2.54 * 300); x++) {
            for (int y = 0; y < imagen.getHeight(); y++) {
                assertEquals(Color.WHITE.getRGB(), imagen.getRGB(x, y));
            }
        }
        // Los últimos 5.5 cm de la colita de 9 cm no contienen texto ni QR.
        for (int x = (int) Math.round(3.5 / 2.54 * 300); x < inicioPrincipal; x++) {
            for (int y = 0; y < imagen.getHeight(); y++) {
                assertEquals(Color.WHITE.getRGB(), imagen.getRGB(x, y));
            }
        }
        var principal = imagen.getSubimage(inicioPrincipal, 0, anchoPrincipal, imagen.getHeight());
        assertEquals(datos.qrContenido(), new MultiFormatReader().decode(new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(principal)))).getText());
        var talon = imagen.getSubimage(inicioTalon, 0, anchoTalon, imagen.getHeight());
        org.junit.jupiter.api.Assertions.assertThrows(com.google.zxing.NotFoundException.class,
                () -> new MultiFormatReader().decode(new BinaryBitmap(
                        new HybridBinarizer(new BufferedImageLuminanceSource(talon)))));
        // Cambiar el QR no debe alterar los datos repetidos en el talón.
        var otro = renderer.renderAdministrativo(new DatosTicketAdministrativo(
                datos.nombreCompleto(), datos.ci(), datos.codigo(), "otro-qr"), FormatoPliego.ADMINISTRATIVO_5);
        int tinta = 0;
        for (int x = inicioTalon; x < anchoTalon; x++) {
            for (int y = 0; y < imagen.getHeight(); y++) {
                assertEquals(imagen.getRGB(x, y), otro.getRGB(x, y));
                if (imagen.getRGB(x, y) != Color.WHITE.getRGB()) tinta++;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(tinta > 0);
    }

    @Test
    void nombreLargoSeDivideEnDosLineasSinPerderDatos() {
        var imagen = new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = imagen.createGraphics();
        try {
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 28));
            String nombre = "Nombre: MARÍA ALEJANDRA FERNÁNDEZ GUTIÉRREZ DE LA TORRE";
            var lineas = TicketRenderer.dividirNombreTalon(nombre, g.getFontMetrics(), 670);
            assertEquals(2, lineas.size());
            assertEquals(nombre, String.join(" ", lineas));
            assertEquals(java.util.List.of("Nombre: ANA"),
                    TicketRenderer.dividirNombreTalon("Nombre: ANA", g.getFontMetrics(), 670));
        } finally {
            g.dispose();
        }
    }

    @Test
    void pliegoAdministrativoTieneCincoEntradasEnHojaDe208Por371() throws Exception {
        for (FormatoPliego formato : FormatoPliego.values()) {
            byte[] pdf = renderer.pdfPliegoAdministrativos(
                    Collections.nCopies(6, datos), formato);
            try (PdfReader reader = new PdfReader(pdf)) {
                assertEquals(2, reader.getNumberOfPages());
                assertEquals(20.8 * 72 / 2.54, reader.getPageSize(1).getWidth(), 0.02);
                assertEquals(37.1 * 72 / 2.54, reader.getPageSize(1).getHeight(), 0.02);
                String contenido = new String(reader.getPageContent(1), java.nio.charset.StandardCharsets.ISO_8859_1);
                var matrices = java.util.regex.Pattern.compile(
                        "([\\d.]+) 0 0 ([\\d.]+) ([\\d.]+) ([\\d.]+) cm").matcher(contenido);
                int cantidad = 0;
                while (matrices.find()) {
                    assertEquals(20.8 * 72 / 2.54, Double.parseDouble(matrices.group(1)), 0.02);
                    assertEquals(7.42 * 72 / 2.54, Double.parseDouble(matrices.group(2)), 0.02);
                    assertEquals(0, Double.parseDouble(matrices.group(3)), 0.02);
                    assertEquals((37.1 - (cantidad + 1) * 7.42) * 72 / 2.54,
                            Double.parseDouble(matrices.group(4)), 0.02);
                    cantidad++;
                }
                assertEquals(5, cantidad);
            }
        }
    }
}
