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

    /** Mots de passe trop courants, refusés même s'ils respectent la longueur. */
    private static final java.util.Set<String> MOTS_DE_PASSE_COURANTS = java.util.Set.of(
            "password", "password1", "password123", "motdepasse", "motdepasse1", "azerty123",
            "azertyuiop", "qwerty123", "qwertyuiop", "12345678", "123456789", "1234567890",
            "11111111", "00000000", "abcd1234", "admin123", "administrateur", "memotdsi",
            "memotdsi1", "tdsi2025", "tdsi2026", "senegal1", "dakar2026", "bienvenue1");

    /**
     * Mot de passe robuste : 8 à 128 caractères, au moins une lettre et un chiffre,
     * absent de la liste des mots de passe courants.
     */
    public static boolean motDePasseRobuste(String motDePasse) {
        if (!longueurEntre(motDePasse, 8, 128)) {
            return false;
        }
        boolean lettre = motDePasse.chars().anyMatch(Character::isLetter);
        boolean chiffre = motDePasse.chars().anyMatch(Character::isDigit);
        return lettre && chiffre
                && !MOTS_DE_PASSE_COURANTS.contains(motDePasse.trim().toLowerCase(java.util.Locale.ROOT));
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
