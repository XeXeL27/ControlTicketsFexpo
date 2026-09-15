package com.uap.control_tickets.Utils.csv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;

/**
 * Utilidades sin estado para leer CSV exportados desde Excel, compartidas por las
 * importaciones de estudiantes y administrativos.
 *
 * Lo delicado que resuelve es la CODIFICACION: el CSV no dice en que codificacion
 * viene, y Excel exporta en varias segun la opcion elegida al guardar
 * (UTF-8, Windows-1252 y CP850 en "CSV MS-DOS"). Adivinar mal no da error, da texto
 * mojado ("Ingenier¡a") que queda mal guardado para siempre. Por eso se intenta
 * UTF-8 estricto y, si falla, gana la codificacion que produce el texto mas plausible
 * en castellano.
 */
public final class CsvUtils {

    private CsvUtils() {}

    private static final Logger log = LoggerFactory.getLogger(CsvUtils.class);

    /**
     * Codificaciones a probar cuando el CSV no es UTF-8, en orden de preferencia
     * ante empate: "CSV (delimitado por comas)" = windows-1252 y "CSV (MS-DOS)" = CP850.
     */
    private static final List<Charset> CANDIDATAS = List.of(
            Charset.forName("windows-1252"),
            Charset.forName("IBM850"));

    /**
     * Letras acentuadas propias del castellano: su presencia indica que acertamos.
     * A proposito NO incluye "¡" ni "¿": son la basura que aparece al leer un CP850
     * como windows-1252 ("Ingenier¡a"), asi que premiarlas elegiria la equivocada.
     */
    private static final String LETRAS_CASTELLANO = "áéíóúüñÁÉÍÓÚÜÑ";

    /** Igual que {@link #decodificarConNombre}, pero devuelve solo el texto. */
    public static String decodificar(byte[] bytes) {
        return decodificarConNombre(bytes)[1];
    }

    /**
     * Decodifica los bytes del CSV respetando tildes y "ñ".
     * Devuelve {nombreDeLaCodificacion, texto}. Primero prueba UTF-8 ESTRICTO
     * (que falla si los bytes no son UTF-8 valido); si falla, elige entre las
     * candidatas la que produce el texto mas plausible en castellano (ver puntuar()).
     */
    public static String[] decodificarConNombre(byte[] bytes) {
        try {
            CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return new String[]{"UTF-8", utf8.decode(ByteBuffer.wrap(bytes)).toString()};
        } catch (CharacterCodingException e) {
            Charset elegida = CANDIDATAS.get(0);
            String mejorTexto = new String(bytes, elegida);
            int mejorPuntaje = puntuar(mejorTexto);
            for (Charset cs : CANDIDATAS.subList(1, CANDIDATAS.size())) {
                String texto = new String(bytes, cs);
                int puntaje = puntuar(texto);
                if (puntaje > mejorPuntaje) {
                    mejorPuntaje = puntaje;
                    mejorTexto = texto;
                    elegida = cs;
                }
            }
            log.info("El CSV no es UTF-8; se interpreta como {} (puntaje {}).", elegida, mejorPuntaje);
            return new String[]{elegida.name(), mejorTexto};
        }
    }

    /**
     * Que tan plausible es que este texto sea castellano bien decodificado.
     * Premia las letras acentuadas del idioma y castiga los simbolos raros y los
     * caracteres de reemplazo, firma de una decodificacion errada.
     */
    private static int puntuar(String texto) {
        int puntaje = 0;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < 128) continue;                       // ASCII: no aporta ni resta
            if (LETRAS_CASTELLANO.indexOf(c) >= 0) puntaje += 2;
            else if (c == '�') puntaje -= 5;        // no se pudo mapear: casi seguro la equivocada
            else puntaje -= 1;                           // simbolo inesperado en nombres/carreras
        }
        return puntaje;
    }

    /** Separador mas frecuente entre ';' y ',' (por defecto ','). */
    public static char detectarSeparador(String cabecera) {
        long comas = cabecera.chars().filter(c -> c == ',').count();
        long puntos = cabecera.chars().filter(c -> c == ';').count();
        return puntos > comas ? ';' : ',';
    }

    /** Quita el BOM (U+FEFF) si el archivo empieza con el. */
    public static String quitarBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    /** Minusculas, sin tildes y sin puntuacion: "R.U." -> "ru", "Nº RU" -> "nru". */
    public static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Detecta texto con pinta de mal decodificado, para avisar ANTES de guardarlo.
     * El caracter de reemplazo y los signos "¡ ¢ £ ¤ ¥" en medio de una palabra son
     * la firma tipica de un CSV leido con la codificacion equivocada.
     * Devuelve el motivo, o null si el texto se ve bien.
     */
    public static String textoSospechoso(String... valores) {
        for (String v : valores) {
            if (v == null) continue;
            if (v.indexOf('�') >= 0) return "Texto ilegible: el archivo esta dañado";
            for (char c : "¡¢£¤¥".toCharArray()) {
                if (v.indexOf(c) >= 0) return "Posible problema de codificación";
            }
        }
        return null;
    }

    /** Celda i de una fila ya separada, recortada; null si esta vacia o no existe. */
    public static String get(String[] campos, int i) {
        if (i >= campos.length) return null;
        String v = campos[i].trim();
        return v.isEmpty() ? null : v;
    }

    /** Separa una linea por el separador dado, conservando celdas vacias al final. */
    public static String[] separar(String linea, char sep) {
        return linea.split(java.util.regex.Pattern.quote(String.valueOf(sep)), -1);
    }
}
