package com.tdsi.memotheque.util;

import java.text.Normalizer;
import java.util.Locale;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Utilisateur;

/** Références bibliographiques d'un mémoire (APA 7 et BibTeX). */
public final class CitationUtil {

    public static final String ETABLISSEMENT =
            "Faculté TDSI (Transmission de Données et Sécurité de l'Information)";

    private CitationUtil() { }

    /** Ex. : Diallo, A. (2025). Titre du mémoire [Mémoire de Master 2, Faculté TDSI]. */
    public static String apa(Memoire m) {
        StringBuilder sb = new StringBuilder();
        sb.append(auteurApa(m.getAuteur()));
        if (m.getCoAuteur() != null && !m.getCoAuteur().isBlank()) {
            sb.append(", & ").append(m.getCoAuteur().trim());
        }
        sb.append(" (").append(annee(m)).append("). ");
        sb.append(m.getTitre()).append(" [").append(type(m.getNiveau())).append(", ")
                .append(ETABLISSEMENT).append("].");
        return sb.toString();
    }

    public static String bibtex(Memoire m) {
        boolean master = m.getNiveau() == Niveau.M1 || m.getNiveau() == Niveau.M2;
        StringBuilder auteurs = new StringBuilder(auteurBibtex(m.getAuteur()));
        if (m.getCoAuteur() != null && !m.getCoAuteur().isBlank()) {
            auteurs.append(" and ").append(echapper(m.getCoAuteur().trim()));
        }
        return "@" + (master ? "mastersthesis" : "misc") + "{" + cle(m) + ",\n"
                + "  author = {" + auteurs + "},\n"
                + "  title  = {{" + echapper(m.getTitre()) + "}},\n"
                + (master ? "  school = {" : "  howpublished = {") + echapper(ETABLISSEMENT) + "},\n"
                + "  type   = {" + echapper(type(m.getNiveau())) + "},\n"
                + "  year   = {" + annee(m) + "},\n"
                + (m.getMotsCles() == null ? "" : "  keywords = {" + echapper(m.getMotsCles()) + "},\n")
                + "  note   = {Disponible sur MémoTDSI}\n"
                + "}";
    }

    /** Année de soutenance : la seconde année académique (2024-2025 -> 2025). */
    static String annee(Memoire m) {
        String a = m.getAnneeAcademique();
        return a != null && a.matches("\\d{4}-\\d{4}") ? a.substring(5) : "s.d.";
    }

    static String type(Niveau n) {
        if (n == null) {
            return "Mémoire";
        }
        return switch (n) {
            case L1, L2, L3 -> "Mémoire de Licence " + n.name().charAt(1);
            case M1, M2 -> "Mémoire de Master " + n.name().charAt(1);
        };
    }

    /** Caractères spéciaux LaTeX échappés : un titre ne peut pas casser le fichier .bib. */
    static String echapper(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\textbackslash{}");
                case '{', '}', '%', '&', '$', '#', '_' -> sb.append('\\').append(c);
                case '^' -> sb.append("\\^{}");
                case '~' -> sb.append("\\~{}");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String auteurApa(Utilisateur u) {
        if (u == null || u.getNom() == null) {
            return "Anonyme";
        }
        String prenom = u.getPrenom() == null || u.getPrenom().isBlank() ? "" : ", " + u.getPrenom().trim().charAt(0) + ".";
        return u.getNom().trim() + prenom;
    }

    private static String auteurBibtex(Utilisateur u) {
        if (u == null || u.getNom() == null) {
            return "Anonyme";
        }
        return echapper(u.getNom().trim()) + (u.getPrenom() == null ? "" : ", " + echapper(u.getPrenom().trim()));
    }

    /** Clé BibTeX ASCII : nom + année + premier mot du titre (ex. diallo2025chiffrement). */
    private static String cle(Memoire m) {
        String nom = m.getAuteur() == null || m.getAuteur().getNom() == null ? "anonyme" : m.getAuteur().getNom();
        String mot = m.getTitre() == null ? "memoire" : m.getTitre().trim().split("\\s+")[0];
        return ascii(nom) + annee(m).replaceAll("\\D", "") + ascii(mot);
    }

    private static String ascii(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
