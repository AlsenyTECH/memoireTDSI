package com.tdsi.memotheque.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

/**
 * Saisie des dates et des heures : format français jj/mm/aaaa quelle que soit la langue du système,
 * et une date tapée au clavier est prise en compte même sans appuyer sur « Entrée »
 * (par défaut, JavaFX l'ignore si l'on clique directement sur un bouton).
 */
public final class Dates {

    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private Dates() { }

    /** Format français et validation de la saisie quand le champ perd le focus. */
    public static DatePicker preparer(DatePicker d) {
        d.setPromptText("jj/mm/aaaa");
        d.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : date.format(JJ_MM_AAAA);
            }

            @Override
            public LocalDate fromString(String texte) {
                try {
                    return analyser(texte);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        });
        d.getEditor().focusedProperty().addListener((o, avant, focus) -> {
            if (!focus) {
                try {
                    d.setValue(analyser(d.getEditor().getText()));
                } catch (IllegalArgumentException e) {
                    // laissé tel quel : lire() signalera l'erreur au moment d'enregistrer
                }
            }
        });
        return d;
    }

    /** Valeur réellement saisie (texte compris), null si vide ; IllegalArgumentException si invalide. */
    public static LocalDate lire(DatePicker d) {
        String texte = d.getEditor().getText();
        if (texte == null || texte.isBlank()) {
            return d.isEditable() ? null : d.getValue();
        }
        LocalDate v = analyser(texte);
        d.setValue(v);
        return v;
    }

    /** Accepte 30/06/2027, 30-06-2027, 30.06.2027, 2027-06-30 et 3/6/2027. */
    public static LocalDate analyser(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        String t = texte.trim();
        try {
            if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(t);
            }
            String[] p = t.split("[/.\\-]");
            if (p.length == 3) {
                return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            }
        } catch (RuntimeException e) {
            // date impossible (31/02...) : message ci-dessous
        }
        throw new IllegalArgumentException("Date invalide « " + t + " » : utilisez le format jj/mm/aaaa.");
    }

    /** Accepte 9:30, 09:30, 9h30, 9h, 9 H 30 et 14. */
    public static LocalTime heure(String texte) {
        String t = texte == null ? "" : texte.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        try {
            String[] p = t.split("[:hH]", -1);
            int h = Integer.parseInt(p[0]);
            int m = p.length > 1 && !p[1].isEmpty() ? Integer.parseInt(p[1]) : 0;
            return LocalTime.of(h, m);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Heure invalide « " + texte + " » : utilisez par exemple 09:30 ou 9h30.");
        }
    }

    public static String format(LocalDate d) {
        return d == null ? "" : d.format(JJ_MM_AAAA);
    }

}
