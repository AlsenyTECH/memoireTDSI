package com.tdsi.memotheque.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Export CSV compatible Excel / LibreOffice (séparateur « ; », UTF-8 avec BOM).
 *
 * Protection contre l'injection de formules (CSV injection) : une cellule commençant par
 * = + - @ tabulation ou retour chariot est préfixée d'une apostrophe, sinon un titre de mémoire
 * comme « =HYPERLINK(...) » serait exécuté par le tableur à l'ouverture.
 */
public final class CsvUtil {

    private static final char SEPARATEUR = ';';

    private CsvUtil() { }

    public static String cellule(Object valeur) {
        if (valeur == null) {
            return "";
        }
        String s = valeur.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.indexOf(SEPARATEUR) >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    public static String ligne(Object... valeurs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < valeurs.length; i++) {
            if (i > 0) {
                sb.append(SEPARATEUR);
            }
            sb.append(cellule(valeurs[i]));
        }
        return sb.toString();
    }

    /** Écrit l'en-tête puis les lignes (chaque ligne est un tableau de valeurs). */
    public static void ecrire(Path fichier, Object[] entete, List<Object[]> lignes) throws IOException {
        try (Writer w = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
            w.write('﻿');   // BOM : Excel reconnaît l'UTF-8 (accents)
            w.write(ligne(entete));
            w.write("\r\n");
            for (Object[] l : lignes) {
                w.write(ligne(l));
                w.write("\r\n");
            }
        }
    }
}
