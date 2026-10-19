package com.tdsi.memotheque.util;

import java.util.regex.Pattern;

/** [M1] Validations de champs réutilisables par les contrôleurs et les services. */
public final class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern MATRICULE = Pattern.compile("^[A-Za-z0-9-]{4,20}$");
    private static final Pattern ANNEE_ACADEMIQUE = Pattern.compile("^(\\d{4})-(\\d{4})$");

    private Validator() { }

    public static boolean estVide(String s) {
        return s == null || s.isBlank();
    }

    public static boolean emailValide(String email) {
        return !estVide(email) && EMAIL.matcher(email.trim()).matches();
    }

    public static boolean matriculeValide(String matricule) {
        return !estVide(matricule) && MATRICULE.matcher(matricule.trim()).matches();
    }

    /** Format AAAA-AAAA avec deux années consécutives (ex. 2025-2026). */
    public static boolean anneeAcademiqueValide(String annee) {
        if (estVide(annee)) {
            return false;
        }
        var m = ANNEE_ACADEMIQUE.matcher(annee.trim());
        if (!m.matches()) {
            return false;
        }
        return Integer.parseInt(m.group(2)) == Integer.parseInt(m.group(1)) + 1;
    }

    /** Vrai si la longueur du texte (sans espaces aux extrémités) est entre min et max inclus. */
    public static boolean longueurEntre(String s, int min, int max) {
        if (s == null) {
            return false;
        }
        int n = s.trim().length();
        return n >= min && n <= max;
    }

    /** Nombre de mots-clés séparés par des virgules (les éléments vides sont ignorés). */
    public static int compterMotsCles(String motsCles) {
        if (estVide(motsCles)) {
            return 0;
        }
        int n = 0;
        for (String mot : motsCles.split(",")) {
            if (!mot.isBlank()) {
                n++;
            }
        }
        return n;
    }
}
