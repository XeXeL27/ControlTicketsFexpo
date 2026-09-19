package com.uap.control_tickets.Utils.ticket;

import org.openpdf.text.Document;
import org.openpdf.text.Image;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;
import com.uap.control_tickets.Utils.qr.QrGenerator;
import com.uap.control_tickets.enums.FormatoPliego;
import com.uap.control_tickets.exception.NegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

/**
 * Rellena la plantilla del ticket de estudiante con los datos + el QR y la
 * exporta a PNG o PDF.
 *
 * Como funciona:
 *  1) Carga la plantilla en blanco desde resources/plantillas/.
 *  2) Dibuja encima cada dato, centrado dentro de su recuadro (coordenadas
 *     detectadas sobre la imagen 2524x839).
 *  3) Incrusta el QR (generado con ZXing) en su recuadro.
 *
 * Las coordenadas son [x, y, ancho, alto] en pixeles de la plantilla original.
 */
@Component
@RequiredArgsConstructor
public class TicketRenderer {

    private final QrGenerator qrGenerator;

    private static final String PLANTILLA_ESTUDIANTE = "plantillas/ticket-estudiante-plantilla.png";

    // Recuadros de la plantilla de estudiante (x, y, ancho, alto)
    private static final int[] CAJA_CARRERA = {520, 64, 704, 68};
    private static final int[] CAJA_CODIGO  = {2144, 64, 312, 68};
    // Mismo código, repetido en el talón izquierdo (recuadro vertical).
    // Interior exacto del recuadro impreso en el talón: sus bordes están en
    // x=342 y x=415, y=27 y y=382 (medido sobre la plantilla en blanco).
    private static final int[] CAJA_CODIGO_TALON = {343, 28, 72, 354};
    private static final int[] CAJA_NOMBRE  = {1252, 148, 1208, 68};
    private static final int[] CAJA_RU      = {856, 160, 348, 52};
    private static final int[] CAJA_QR      = {1344, 420, 264, 260};

    // -------------------------------------------------------------------------
    // API publica
    // -------------------------------------------------------------------------

    /** Ticket de estudiante como imagen (para previsualizar o incrustar). */
    public BufferedImage renderEstudiante(DatosTicketEstudiante datos) {
        BufferedImage base = cargarPlantilla(PLANTILLA_ESTUDIANTE);
        BufferedImage lienzo = new BufferedImage(
                base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_RGB);

        Graphics2D g = lienzo.createGraphics();
        activarCalidad(g);
        g.drawImage(base, 0, 0, null);

        // Textos (negro, negrita, centrados y auto-ajustados a su recuadro)
        dibujarCentrado(g, mayus(datos.carrera()),        CAJA_CARRERA, 40);
        dibujarCentrado(g, mayus(datos.codigo()),         CAJA_CODIGO,  34);
        dibujarCentrado(g, mayus(datos.nombreCompleto()), CAJA_NOMBRE,  44);
        dibujarCentrado(g, datos.ru(),                    CAJA_RU,      40);
        // El mismo código, en el talón izquierdo (texto vertical).
        dibujarVertical(g, mayus(datos.codigo()),         CAJA_CODIGO_TALON, 40);

        // QR
        int lado = Math.min(CAJA_QR[2], CAJA_QR[3]);
        BufferedImage qr = qrGenerator.generarImagen(datos.qrContenido(), lado);
        int qrX = CAJA_QR[0] + (CAJA_QR[2] - lado) / 2;
        int qrY = CAJA_QR[1] + (CAJA_QR[3] - lado) / 2;
        g.setColor(Color.WHITE);
        g.fillRect(qrX, qrY, lado, lado); // fondo blanco por si acaso
        g.drawImage(qr, qrX, qrY, lado, lado, null);

        g.dispose();
        return lienzo;
    }

    /** Ticket de estudiante como PNG. */
    public byte[] pngEstudiante(DatosTicketEstudiante datos) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(renderEstudiante(datos), "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new NegocioException("No se pudo generar el PNG del ticket: " + e.getMessage());
        }
    }

    /** Ticket de estudiante como PDF (una pagina del tamano exacto del ticket). */
    public byte[] pdfEstudiante(DatosTicketEstudiante datos) {
        byte[] png = pngEstudiante(datos);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Image imagen = Image.getInstance(png);
            Rectangle pagina = new Rectangle(imagen.getWidth(), imagen.getHeight());
            Document doc = new Document(pagina, 0, 0, 0, 0);
            PdfWriter.getInstance(doc, out);
            doc.open();
            imagen.setAbsolutePosition(0, 0);
            doc.add(imagen);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new NegocioException("No se pudo generar el PDF del ticket: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Pliego de impresion (varios tickets por hoja)
    // -------------------------------------------------------------------------

    /** Hoja OFICIO en vertical, en centimetros. */
    private static final double HOJA_ANCHO_CM = 21.5;
    private static final double HOJA_ALTO_CM = 33.0;
    private static final double HOJA_ADMIN_ANCHO_CM = 20.8;
    private static final double HOJA_ADMIN_ALTO_CM = 37.1;
    /** Margen de la hoja y separacion entre tickets, para que la imprenta pueda cortar. */
    private static final double MARGEN_CM = 0.3;
    private static final double SEPARACION_CM = 0.2;

    /** 1 cm = 28.3465 puntos PostScript (72 puntos por pulgada). */
    private static final double PUNTOS_POR_CM = 72.0 / 2.54;

    /**
     * Resolucion de impresion. 300 DPI es el estandar de imprenta; por encima de eso
     * el ojo no distingue y el archivo crece sin sentido.
     */
    private static final int DPI_IMPRESION = 300;

    /**
     * Calidad JPEG de los tickets dentro del pliego.
     *
     * El arte es una FOTO (degradados, caras), que en PNG no comprime casi nada: cada
     * ticket crudo pesa 6 MB y una hoja de 8 se iba a 22 MB, o sea medio giga para
     * todo el evento. En JPEG 0.9 la diferencia visual es imperceptible en papel y el
     * archivo queda en un tamano que se puede mandar por correo a la imprenta.
     */
    private static final float CALIDAD_JPEG = 0.9f;

    private static float pt(double cm) {
        return (float) (cm * PUNTOS_POR_CM);
    }

    /**
     * Arma el PDF con varios tickets acomodados en hojas OFICIO, listo para imprenta.
     *
     * Reparte los tickets en hojas de {@code formato.getPorHoja()}. En cada hoja
     * primero se llenan las posiciones horizontales (apiladas, de arriba hacia
     * abajo) y despues las verticales de la columna lateral, rotadas 90 grados.
     *
     * El PDF se arma en PUNTOS (unidad real de PostScript), no en pixeles, para que
     * el ticket salga impreso exactamente del tamano fisico que pide la imprenta.
     */
    public byte[] pdfPliegoEstudiantes(List<DatosTicketEstudiante> tickets, FormatoPliego formato) {
        return pdfPliego(tickets, formato, datos -> jpegParaImpresion(datos, formato));
    }

    /** Solo reversos: datos a la izquierda y QR a la derecha, sin arte ni anversos. */
    public byte[] pdfPliegoAdministrativos(List<DatosTicketAdministrativo> tickets, FormatoPliego formato) {
        return pdfPliego(tickets, FormatoPliego.ADMINISTRATIVO_5, datos -> {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(renderAdministrativo(datos, formato), "PNG", out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new NegocioException("No se pudo generar el reverso: " + e.getMessage());
            }
        });
    }

    public BufferedImage renderAdministrativo(DatosTicketAdministrativo datos, FormatoPliego formato) {
        String[] lineas = {
                "Nombre: " + mayus(datos.nombreCompleto()), "CI: " + mayus(datos.ci()),
                "Código: " + mayus(datos.codigo())};
        BufferedImage imagen = new BufferedImage(pixeles(20.8), pixeles(7.42), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
            // Bloque de 3.2 cm de alto centrado en los 7.42 cm del ticket.
            // Sin título: solo datos + QR (medidas y QR intactos).
            int y = (imagen.getHeight() - pixeles(3.2)) / 2;
            BufferedImage principal = renderReverso("", lineas, datos.qrContenido(), 11.8, 3.2, true);
            // Girar solo el bloque principal 180° dentro de su misma área.
            g.drawImage(principal, imagen.getWidth(), y + principal.getHeight(),
                    -principal.getWidth(), -principal.getHeight(), null);
            // Talón de 9 cm: 3.5 cm para datos y 5.5 cm vacíos a su derecha. Cuerpo de 11.8 cm.
            dibujarTalonVertical(g, lineas, 0, imagen.getHeight());
        } finally {
            g.dispose();
        }
        return imagen;
    }

    /** Texto vertical en los primeros 3.5 cm del talón; sus 5.5 cm derechos quedan vacíos. */
    private void dibujarTalonVertical(Graphics2D destino, String[] lineas, int inicioTalon, int alto) {
        Graphics2D g = (Graphics2D) destino.create();
        try {
            activarCalidad(g);
            int anchoTexto = alto - 2 * pixeles(0.4);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            java.util.List<String> textos = new java.util.ArrayList<>();
            // Sin la palabra "ADMINISTRATIVO": solo los datos del titular.
            textos.addAll(dividirNombreTalon(lineas[0], g.getFontMetrics(), anchoTexto));
            textos.add(lineas[1]);
            textos.add(lineas[2]);
            int paso = g.getFontMetrics().getAscent() + g.getFontMetrics().getDescent();
            g.translate(inicioTalon + pixeles(3.5) - pixeles(0.2) - paso * textos.size(), alto - pixeles(0.4));
            g.rotate(-Math.PI / 2);
            g.setColor(Color.BLACK);
            int y = 0;
            for (String texto : textos) {
                g.setFont(ajustar(g, texto, anchoTexto, paso, 28));
                FontMetrics fm = g.getFontMetrics();
                // Centrado en el largo del talón: así cada renglón queda centrado
                // verticalmente en el ticket, a la altura del QR (que va centrado
                // y no se toca). Medidas y QR intactos.
                g.drawString(texto, (anchoTexto - fm.stringWidth(texto)) / 2, y + fm.getAscent());
                // Solo ascenso y descenso: sin separación adicional entre renglones.
                y += paso;
            }
        } finally {
            g.dispose();
        }
    }

    static List<String> dividirNombreTalon(String texto, FontMetrics fm, int ancho) {
        if (fm.stringWidth(texto) <= ancho) return List.of(texto);
        // Elegir el espacio que equilibra mejor las dos líneas, sin perder caracteres.
        int corte = -1;
        int mejorAncho = Integer.MAX_VALUE;
        for (int i = 1; i < texto.length() - 1; i++) {
            if (texto.charAt(i) != ' ') continue;
            int mayor = Math.max(fm.stringWidth(texto.substring(0, i)), fm.stringWidth(texto.substring(i + 1)));
            if (mayor < mejorAncho) { mejorAncho = mayor; corte = i; }
        }
        if (corte < 0) corte = texto.length() / 2;
        return List.of(texto.substring(0, corte).stripTrailing(), texto.substring(corte).stripLeading());
    }

    public BufferedImage renderDocente(DatosTicketDocente datos, FormatoPliego formato) {
        return renderReverso("DOCENTE", new String[]{
                "Nombre: " + mayus(datos.nombreCompleto()), "CI: " + mayus(datos.ci()),
                "Código docente: " + mayus(datos.codigoDocente()),
                "Carrera: " + mayus(datos.carrera()), "Ticket: " + mayus(datos.codigoTicket())
        }, datos.qrContenido(), formato);
    }

    public byte[] pdfPliegoDocentes(List<DatosTicketDocente> tickets, FormatoPliego formato) {
        return pdfPliego(tickets, formato, datos -> aPng(renderDocente(datos, formato)));
    }

    public byte[] pngDocente(DatosTicketDocente datos) {
        return aPng(renderDocente(datos, FormatoPliego.MIXTO_8));
    }

    public byte[] pdfDocente(DatosTicketDocente datos) {
        FormatoPliego formato = FormatoPliego.MIXTO_8;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(new Rectangle(pt(formato.getLargoCm()), pt(formato.getAltoCm())),
                    0, 0, 0, 0);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Image imagen = Image.getInstance(pngDocente(datos));
            imagen.scaleAbsolute(pt(formato.getLargoCm()), pt(formato.getAltoCm()));
            imagen.setAbsolutePosition(0, 0);
            doc.add(imagen);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new NegocioException("No se pudo generar el ticket docente: " + e.getMessage());
        }
    }

    /**
     * Ticket administrativo suelto (PNG): el mismo reverso del pliego, en la
     * MISMA medida que la grupal (ADMINISTRATIVO_5: 20.8 x 7.42 cm).
     */
    public byte[] pngAdministrativo(DatosTicketAdministrativo datos) {
        return aPng(renderAdministrativo(datos, FormatoPliego.ADMINISTRATIVO_5));
    }

    /**
     * Ticket administrativo suelto (PDF de una página, medida del pliego).
     * Sale GIRADO 90° (vertical: 7.42 de ancho x 20.8 de alto) para que a la
     * impresora entre el lado angosto primero.
     */
    public byte[] pdfAdministrativo(DatosTicketAdministrativo datos) {
        FormatoPliego formato = FormatoPliego.ADMINISTRATIVO_5;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(new Rectangle(pt(formato.getAltoCm()), pt(formato.getLargoCm())),
                    0, 0, 0, 0);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Image imagen = Image.getInstance(pngAdministrativo(datos));
            imagen.setRotationDegrees(90);
            imagen.scaleAbsolute(pt(formato.getAltoCm()), pt(formato.getLargoCm()));
            imagen.setAbsolutePosition(0, 0);
            doc.add(imagen);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new NegocioException("No se pudo generar el ticket administrativo: " + e.getMessage());
        }
    }

    private byte[] aPng(BufferedImage imagen) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(imagen, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new NegocioException("No se pudo generar el PNG del ticket: " + e.getMessage());
        }
    }

    private BufferedImage renderReverso(String titulo, String[] lineas, String qrContenido,
                                        FormatoPliego formato) {
        return renderReverso(titulo, lineas, qrContenido, formato.getLargoCm(), formato.getAltoCm());
    }

    private int pixeles(double cm) {
        return (int) Math.round(cm / 2.54 * DPI_IMPRESION);
    }

    private BufferedImage renderReverso(String titulo, String[] lineas, String qrContenido,
                                        double anchoCm, double altoCm) {
        return renderReverso(titulo, lineas, qrContenido, anchoCm, altoCm, false);
    }

    private BufferedImage renderReverso(String titulo, String[] lineas, String qrContenido,
                                        double anchoCm, double altoCm, boolean derecha) {
        int ancho = pixeles(anchoCm);
        int alto = pixeles(altoCm);
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        try {
            activarCalidad(g);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ancho, alto);
            int margen = pixeles(derecha ? 0.2 : 0.4);
            int ladoQr = (int) Math.round(3.2 / 2.54 * DPI_IMPRESION);
            int xQr = ancho - margen - ladoQr;
            int anchoTexto = qrContenido == null ? ancho - 2 * margen : xQr - 2 * margen;
            g.setFont(ajustar(g, titulo, anchoTexto, margen, 36));
            g.setColor(Color.BLACK);
            g.drawString(titulo, margen + (derecha ? anchoTexto - g.getFontMetrics().stringWidth(titulo) : 0),
                    margen + g.getFontMetrics().getAscent());
            int inicioDatos = 2 * margen + 12;
            int fila = (alto - inicioDatos - margen) / lineas.length;
            for (int i = 0; i < lineas.length; i++) {
                g.setFont(ajustar(g, lineas[i], anchoTexto, fila, 42));
                g.setColor(Color.BLACK);
                FontMetrics fm = g.getFontMetrics();
                int y = inicioDatos + i * fila;
                g.drawString(lineas[i], margen + (derecha ? anchoTexto - fm.stringWidth(lineas[i]) : 0),
                        y + (fila - fm.getHeight()) / 2 + fm.getAscent());
            }
            if (qrContenido != null) {
                g.drawImage(qrGenerator.generarImagen(qrContenido, ladoQr),
                        xQr, (alto - ladoQr) / 2, null);
            }
        } finally {
            g.dispose();
        }
        return imagen;
    }

    private <T> byte[] pdfPliego(List<T> tickets, FormatoPliego formato, Function<T, byte[]> render) {
        if (tickets == null || tickets.isEmpty()) {
            throw new NegocioException("No hay tickets para armar el pliego");
        }
        Rectangle hoja = formato == FormatoPliego.ADMINISTRATIVO_5
                ? new Rectangle(pt(HOJA_ADMIN_ANCHO_CM), pt(HOJA_ADMIN_ALTO_CM))
                : new Rectangle(pt(HOJA_ANCHO_CM), pt(HOJA_ALTO_CM));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(hoja, 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();

            int porHoja = formato.getPorHoja();
            for (int i = 0; i < tickets.size(); i++) {
                int posicion = i % porHoja;
                if (i > 0 && posicion == 0) doc.newPage();
                colocar(lienzo, Image.getInstance(render.apply(tickets.get(i))), formato, posicion);
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new NegocioException("No se pudo generar el pliego: " + e.getMessage());
        }
    }

    /**
     * Dibuja un ticket en la posicion que le toca dentro de la hoja.
     *
     * El sistema de coordenadas del PDF tiene el origen ABAJO a la izquierda, por eso
     * las filas horizontales se cuentan restando desde el borde superior.
     */
    private void colocar(PdfContentByte lienzo, Image img,
                         FormatoPliego formato, int posicion) throws Exception {
        float largo = pt(formato.getLargoCm());
        float alto = pt(formato.getAltoCm());
        float margen = pt(MARGEN_CM);
        float sep = pt(SEPARACION_CM);
        float topeSuperior = pt(HOJA_ALTO_CM) - margen;

        if (formato == FormatoPliego.ADMINISTRATIVO_5) {
            // Cinco entradas cubren exactamente la hoja de 20.8 x 37.1 cm.
            float x = 0;
            float y = pt(HOJA_ADMIN_ALTO_CM - (posicion + 1) * formato.getAltoCm());
            img.scaleAbsolute(largo, alto);
            img.setAbsolutePosition(x, y);
            lienzo.addImage(img);
            return;
        }

        if (posicion < formato.getHorizontales()) {
            // Fila horizontal: apiladas desde arriba hacia abajo.
            float x = margen;
            float y = topeSuperior - (posicion + 1) * alto - posicion * sep;
            img.scaleAbsolute(largo, alto);
            img.setAbsolutePosition(x, y);
            lienzo.addImage(img);
        } else {
            // Columna lateral: el ticket va rotado 90 grados (queda "de pie").
            int idx = posicion - formato.getHorizontales();
            // Rotado, ocupa 'alto' de ancho y 'largo' de altura.
            float x = margen + largo + sep;
            float y = topeSuperior - (idx + 1) * largo - idx * sep;
            // Matriz [a b c d e f]: mapea el cuadrado unitario de la imagen.
            // Girado 90 grados antihorario, el eje ANCHO de la imagen (largo) apunta
            // hacia ARRIBA y el eje ALTO (alto) hacia la IZQUIERDA:
            //   a=0, b=largo  -> el ancho de la imagen sube 'largo'
            //   c=-alto, d=0  -> el alto de la imagen va 'alto' hacia la izquierda
            // Por eso el origen se corre a x+alto: si no, el ticket cae fuera de la hoja.
            lienzo.addImage(img, 0, largo, -alto, 0, x + alto, y);
        }
    }

    /**
     * Rinde el ticket y lo devuelve como JPEG ya escalado a la resolucion de impresion.
     * Evita cargar el PDF con pixeles que la imprenta no va a usar.
     */
    private byte[] jpegParaImpresion(DatosTicketEstudiante datos, FormatoPliego formato) {
        BufferedImage completa = renderEstudiante(datos);
        int anchoDeseado = (int) Math.round(formato.getLargoCm() / 2.54 * DPI_IMPRESION);
        BufferedImage aUsar = anchoDeseado < completa.getWidth()
                ? escalar(completa, anchoDeseado)
                : completa;
        return aJpeg(aUsar);
    }

    /** Reduce la imagen manteniendo la proporcion, con interpolacion suave. */
    private BufferedImage escalar(BufferedImage origen, int anchoNuevo) {
        int altoNuevo = Math.max(1,
                Math.round(origen.getHeight() * (anchoNuevo / (float) origen.getWidth())));
        BufferedImage destino = new BufferedImage(anchoNuevo, altoNuevo, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        activarCalidad(g);
        g.drawImage(origen, 0, 0, anchoNuevo, altoNuevo, null);
        g.dispose();
        return destino;
    }

    /** Comprime a JPEG con la calidad configurada. */
    private byte[] aJpeg(BufferedImage imagen) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream salida = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(CALIDAD_JPEG);
            writer.setOutput(salida);
            writer.write(null, new IIOImage(imagen, null, null), param);
            salida.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new NegocioException("No se pudo comprimir el ticket: " + e.getMessage());
        } finally {
            writer.dispose();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BufferedImage cargarPlantilla(String ruta) {
        try (InputStream in = new ClassPathResource(ruta).getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new NegocioException("Plantilla no legible: " + ruta);
            return img;
        } catch (IOException e) {
            throw new NegocioException("No se encontro la plantilla del ticket: " + ruta);
        }
    }

    private void activarCalidad(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /** Dibuja el texto en negro, centrado dentro de la caja, reduciendo la fuente si no entra. */
    private void dibujarCentrado(Graphics2D g, String texto, int[] caja, int tamMax) {
        if (texto == null || texto.isBlank()) return;
        int x = caja[0], y = caja[1], w = caja[2], h = caja[3];

        Font fuente = ajustar(g, texto, w, h, tamMax);
        g.setFont(fuente);
        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (w - fm.stringWidth(texto)) / 2;
        int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(texto, tx, ty);
    }

    /** Dibuja el texto en negro, vertical (rotado, leyendo de abajo hacia arriba), centrado en la caja. */
    private void dibujarVertical(Graphics2D g, String texto, int[] caja, int tamMax) {
        if (texto == null || texto.isBlank()) return;
        int x = caja[0], y = caja[1], w = caja[2], h = caja[3];

        // Al rotar, el largo del texto ocupa el ALTO de la caja y su grosor el ANCHO.
        Font fuente = ajustar(g, texto, h, w, tamMax);
        java.awt.geom.AffineTransform previa = g.getTransform();
        g.setColor(Color.BLACK);
        g.setFont(fuente);

        // Centrado sobre la TINTA real del texto, no sobre las metricas de la fuente:
        // ascent/descent reservan lugar para tildes y colas que "EST-000001" no usa,
        // asi que centrar por metricas deja el texto corrido un par de pixeles.
        java.awt.geom.Rectangle2D tinta = fuente
                .createGlyphVector(g.getFontRenderContext(), texto)
                .getVisualBounds();

        g.translate(x + w / 2.0, y + h / 2.0);
        g.rotate(-Math.PI / 2); // -90°: lee de abajo hacia arriba

        // getVisualBounds() viene relativa al origen de la linea base: restarla
        // deja el rectangulo de tinta clavado en el centro de la caja.
        g.drawString(texto,
                (float) (-tinta.getWidth() / 2 - tinta.getX()),
                (float) (-tinta.getHeight() / 2 - tinta.getY()));
        g.setTransform(previa);
    }

    /** Busca la fuente mas grande (<= tamMax) que quepa en el ancho/alto de la caja. */
    private Font ajustar(Graphics2D g, String texto, int w, int h, int tamMax) {
        for (int tam = tamMax; tam >= 12; tam -= 2) {
            Font f = new Font(Font.SANS_SERIF, Font.BOLD, tam);
            FontMetrics fm = g.getFontMetrics(f);
            if (fm.stringWidth(texto) <= w * 0.92 && fm.getHeight() <= h) {
                return f;
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, 12);
    }

    private String mayus(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
