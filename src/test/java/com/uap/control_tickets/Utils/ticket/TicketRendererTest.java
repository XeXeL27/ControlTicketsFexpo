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
        for (FormatoPliego formato : FormatoPliego.values()) {
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
            assertEquals(Math.round(formato.getLargoCm() / 2.54 * 300), imagen.getWidth());
            assertEquals(Math.round(formato.getAltoCm() / 2.54 * 300), imagen.getHeight());
            assertEquals(Color.WHITE.getRGB(), imagen.getRGB(0, 0));
            var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen)));
            assertEquals(datos.qrContenido(), new MultiFormatReader().decode(bitmap).getText());
        }
    }

    @Test
    void pliegoTieneSoloReversosEnHojasOficio() throws Exception {
        for (FormatoPliego formato : FormatoPliego.values()) {
            byte[] pdf = renderer.pdfPliegoAdministrativos(
                    Collections.nCopies(formato.getPorHoja() + 1, datos), formato);
            try (PdfReader reader = new PdfReader(pdf)) {
                assertEquals(2, reader.getNumberOfPages());
                assertEquals(21.5 * 72 / 2.54, reader.getPageSize(1).getWidth(), 0.02);
                assertEquals(33 * 72 / 2.54, reader.getPageSize(1).getHeight(), 0.02);
            }
        }
    }
}
