package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.tdsi.memotheque.dao.SoutenanceDAO;
import com.tdsi.memotheque.dao.impl.SoutenanceDAOImpl;
import com.tdsi.memotheque.model.MembreJury;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.RoleJury;
import com.tdsi.memotheque.model.Soutenance;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.SessionManager;

/**
 * Convocations aux soutenances : une lettre pour l'étudiant, une pour chaque membre du jury.
 * La scolarité obtient toutes les lettres d'une soutenance ; l'étudiant et chaque membre du jury
 * n'obtiennent que la leur.
 */
public class ConvocationService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH'h'mm");
    private static final DateTimeFormatter DATE_LETTRE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    public static final int ARRIVEE_AVANT_MINUTES = 30;

    /** Contenu d'une lettre, indépendant de l'affichage (écran ou impression). */
    public record Convocation(String reference, String lieuDate, String destinataire, String qualite,
            String objet, String formuleAppel, List<String> paragraphes, List<String[]> details,
            String formuleFin, String signature) { }

    private final SoutenanceDAO soutenanceDAO;

    public ConvocationService() {
        this(new SoutenanceDAOImpl());
    }

    public ConvocationService(SoutenanceDAO soutenanceDAO) {
        this.soutenanceDAO = Objects.requireNonNull(soutenanceDAO);
    }

    /**
     * Convocations d'une soutenance programmée.
     * Scolarité : l'étudiant puis chaque membre du jury. Étudiant ou membre du jury : la sienne seulement.
     */
    public List<Convocation> convocations(int idMemoire) throws SQLException {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null) {
            throw new SecurityException("Connexion requise.");
        }
        Soutenance s = soutenanceDAO.findByMemoire(idMemoire);
        if (s == null || s.getStatut() != Soutenance.Statut.PLANIFIEE) {
            throw new IllegalStateException("Aucune soutenance programmée pour ce mémoire.");
        }
        Memoire m = s.getMemoire();
        boolean auteur = m.getAuteur() != null && m.getAuteur().getId() == u.getId();
        List<Convocation> lettres = new ArrayList<>();
        if (u.getRole() == Role.ADMIN || auteur) {
            lettres.add(pourEtudiant(s));
        }
        for (MembreJury j : s.getJury()) {
            if (u.getRole() == Role.ADMIN || j.enseignant().getId() == u.getId()) {
                lettres.add(pourMembre(s, j));
            }
        }
        if (lettres.isEmpty()) {
            throw new SecurityException("Cette convocation ne vous est pas adressée.");
        }
        return lettres;
    }

    Convocation pourEtudiant(Soutenance s) {
        Memoire m = s.getMemoire();
        List<String> p = List.of(
                "Nous avons l'honneur de vous convoquer à la soutenance publique de votre mémoire de "
                        + niveau(m) + " intitulé « " + m.getTitre() + " ».",
                "La soutenance aura lieu le " + quand(s) + " (" + s.getSalle() + "), devant le jury ci-dessous.",
                "Vous voudrez bien vous présenter " + ARRIVEE_AVANT_MINUTES + " minutes avant le début de la séance, "
                        + "muni(e) de votre carte d'étudiant et de votre support de présentation. L'exposé dure "
                        + "20 minutes et sera suivi des questions du jury.");
        return new Convocation(reference(s, "E"), lieuDate(), m.getAuteurNomComplet(), "Candidat(e)",
                "Convocation à la soutenance de votre mémoire", "Madame, Monsieur,", p, details(s),
                "Nous vous prions d'agréer, Madame, Monsieur, l'expression de nos salutations distinguées.",
                "Le service de la scolarité");
    }

    Convocation pourMembre(Soutenance s, MembreJury j) {
        Memoire m = s.getMemoire();
        String qualite = qualite(j.role());
        List<String> p = List.of(
                "Nous avons l'honneur de vous convier, en qualité " + deLe(qualite.toLowerCase(Locale.ROOT))
                        + ", à la soutenance du mémoire de " + niveau(m) + " de " + m.getAuteurNomComplet()
                        + ", intitulé « " + m.getTitre() + " ».",
                "La soutenance aura lieu le " + quand(s) + " (" + s.getSalle() + ").",
                "Le mémoire est consultable dans MémoTDSI, rubrique « Mes soutenances (jury) ». "
                        + "Nous vous remercions de bien vouloir confirmer votre disponibilité auprès de la scolarité.");
        return new Convocation(reference(s, j.role().name().substring(0, 1)), lieuDate(),
                j.enseignant().getNomComplet(), qualite, "Convocation à un jury de soutenance",
                "Madame, Monsieur,", p, details(s),
                "Nous vous prions d'agréer, Madame, Monsieur, l'expression de notre haute considération.",
                "Le service de la scolarité");
    }

    private static List<String[]> details(Soutenance s) {
        Memoire m = s.getMemoire();
        List<String[]> d = new ArrayList<>();
        d.add(new String[] {"Date", majuscule(s.getDateHeure().format(JOUR))});
        d.add(new String[] {"Heure", s.getDateHeure().format(HEURE) + " (durée " + Soutenance.DUREE_MINUTES + " min)"});
        d.add(new String[] {"Salle", s.getSalle()});
        d.add(new String[] {"Candidat(e)", m.getAuteurNomComplet() + " — " + niveau(m) + " " + m.getAnneeAcademique()});
        d.add(new String[] {"Mémoire", m.getTitre()});
        for (RoleJury r : RoleJury.values()) {
            for (MembreJury j : s.getJury()) {
                if (j.role() == r) {
                    d.add(new String[] {qualite(r), j.enseignant().getNomComplet()});
                }
            }
        }
        return d;
    }

    static String qualite(RoleJury r) {
        return switch (r) {
            case PRESIDENT -> "Président du jury";
            case EXAMINATEUR -> "Examinateur";
            case ENCADREUR -> "Encadreur";
        };
    }

    /** « d'encadreur », « d'examinateur », « de président du jury ». */
    private static String deLe(String nom) {
        return ("aeiouéè".indexOf(nom.charAt(0)) >= 0 ? "d'" : "de ") + nom;
    }

    private static String quand(Soutenance s) {
        return s.getDateHeure().format(JOUR) + " à " + s.getDateHeure().format(HEURE);
    }

    private static String niveau(Memoire m) {
        return m.getNiveau() == null ? "fin d'études" : m.getNiveau().name();
    }

    private static String reference(Soutenance s, String suffixe) {
        return "Réf. SOUT-" + s.getMemoire().getAnneeAcademique() + "-" + s.getIdMemoire() + "-" + suffixe;
    }

    private static String lieuDate() {
        return "Dakar, le " + LocalDate.now().format(DATE_LETTRE);
    }

    private static String majuscule(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
