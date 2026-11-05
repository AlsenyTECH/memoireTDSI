package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.tdsi.memotheque.dao.ParametreDAO;
import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.dao.impl.ParametreDAOImpl;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.Dates;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Validator;

/**
 * Paramètres de la scolarité : année académique en cours et campagne de dépôt.
 * La campagne se règle niveau par niveau (L1 ... M2) : ouverte ou fermée, avec sa propre date limite,
 * que la scolarité peut prolonger ou écourter. Les étudiants du niveau sont notifiés de chaque changement.
 * Un niveau sans réglage propre suit le réglage général.
 */
public class ParametreService {

    public static final String ANNEE_COURANTE = "annee_courante";
    public static final String DEPOT_OUVERT = "depot_ouvert";
    public static final String DATE_LIMITE_DEPOT = "date_limite_depot";

    /** État de la campagne (générale ou d'un niveau), pour l'affichage. */
    public record Campagne(String anneeCourante, boolean ouverte, LocalDate dateLimite) {
        public boolean depotPossible(LocalDate aujourdhui) {
            return ouverte && (dateLimite == null || !aujourdhui.isAfter(dateLimite));
        }

        /** « Ouverte jusqu'au 30/06/2027 », « Ouverte (sans date limite) », « Fermée », « Date limite dépassée ». */
        public String etat(LocalDate aujourdhui) {
            if (!ouverte) {
                return "Fermée";
            }
            if (dateLimite == null) {
                return "Ouverte (sans date limite)";
            }
            return aujourdhui.isAfter(dateLimite) ? "Date limite dépassée (" + Dates.format(dateLimite) + ")"
                    : "Ouverte jusqu'au " + Dates.format(dateLimite);
        }
    }

    private final ParametreDAO dao;
    private final JournalService journalService;
    private final UtilisateurDAO utilisateurDAO;
    private final NotificationService notifications;

    public ParametreService() {
        this(new ParametreDAOImpl(), new JournalService());
    }

    public ParametreService(ParametreDAO dao, JournalService journalService) {
        this.dao = Objects.requireNonNull(dao);
        this.journalService = Objects.requireNonNull(journalService);
        this.utilisateurDAO = new UtilisateurDAOImpl();
        this.notifications = new NotificationService();
    }

    // ------------------------------------------------------------------ lecture

    /** Réglage général (et année en cours). */
    public Campagne campagne() throws SQLException {
        String annee = dao.lire(ANNEE_COURANTE);
        String limite = dao.lire(DATE_LIMITE_DEPOT);
        return new Campagne(
                Validator.anneeAcademiqueValide(annee) ? annee : anneeParDefaut(LocalDate.now()),
                !"0".equals(dao.lire(DEPOT_OUVERT)),
                limite == null || limite.isBlank() ? null : LocalDate.parse(limite));
    }

    /** Campagne d'un niveau : son réglage propre s'il existe, sinon le réglage général. */
    public Campagne campagne(Niveau niveau) throws SQLException {
        Campagne g = campagne();
        if (niveau == null) {
            return g;
        }
        String ouverte = dao.lire(cleOuvert(niveau));
        String limite = dao.lire(cleLimite(niveau));
        return new Campagne(g.anneeCourante(),
                ouverte == null ? g.ouverte() : "1".equals(ouverte),
                limite == null ? g.dateLimite() : limite.isBlank() ? null : LocalDate.parse(limite));
    }

    /** Campagne de chaque niveau, de L1 à M2. */
    public Map<Niveau, Campagne> campagnes() throws SQLException {
        Map<Niveau, Campagne> m = new EnumMap<>(Niveau.class);
        for (Niveau n : Niveau.values()) {
            m.put(n, campagne(n));
        }
        return m;
    }

    /** « Dépôts ouverts : M1 (jusqu'au 30/06/2027), M2 (jusqu'au 15/07/2027) » ou « Aucun dépôt ouvert ». */
    public String resume(LocalDate aujourdhui) throws SQLException {
        List<String> ouverts = new ArrayList<>();
        for (Map.Entry<Niveau, Campagne> e : campagnes().entrySet()) {
            if (e.getValue().depotPossible(aujourdhui)) {
                ouverts.add(e.getKey() + (e.getValue().dateLimite() == null ? ""
                        : " (jusqu'au " + Dates.format(e.getValue().dateLimite()) + ")"));
            }
        }
        return ouverts.isEmpty() ? "Aucun dépôt ouvert" : "Dépôts ouverts : " + String.join(", ", ouverts);
    }

    public String anneeCourante() throws SQLException {
        return campagne().anneeCourante();
    }

    /** Refuse un dépôt hors campagne générale (fermée ou date limite dépassée). */
    public void verifierDepotPossible() throws SQLException {
        verifier(campagne(), null);
    }

    /** Refuse un dépôt hors campagne du niveau de l'étudiant. */
    public void verifierDepotPossible(Niveau niveau) throws SQLException {
        verifier(campagne(niveau), niveau);
    }

    private static void verifier(Campagne c, Niveau niveau) {
        String pour = niveau == null ? "" : " pour le niveau " + niveau;
        if (!c.ouverte()) {
            throw new IllegalStateException("La campagne de dépôt est fermée" + pour + " par la scolarité.");
        }
        if (c.dateLimite() != null && LocalDate.now().isAfter(c.dateLimite())) {
            throw new IllegalStateException("La date limite de dépôt" + pour + " (" + Dates.format(c.dateLimite())
                    + ") est dépassée. Contactez la scolarité.");
        }
    }

    // ------------------------------------------------------------------ réglage (administration)

    /** Année en cours et réglage général, appliqué à tous les niveaux (les réglages propres sont effacés). */
    public void definir(String annee, boolean ouverte, LocalDate dateLimite) throws SQLException {
        Utilisateur u = exigerAdmin();
        ecrireAnnee(annee);
        dao.ecrire(DEPOT_OUVERT, ouverte ? "1" : "0");
        dao.ecrire(DATE_LIMITE_DEPOT, dateLimite == null ? null : dateLimite.toString());
        for (Niveau n : Niveau.values()) {
            dao.ecrire(cleOuvert(n), null);
            dao.ecrire(cleLimite(n), null);
        }
        journalService.log(u.getId(), "PARAMETRES", "Année " + annee.trim() + ", dépôt "
                + (ouverte ? "ouvert" : "fermé") + (dateLimite == null ? "" : " jusqu'au " + dateLimite));
    }

    public void definirAnnee(String annee) throws SQLException {
        Utilisateur u = exigerAdmin();
        if (ecrireAnnee(annee)) {
            journalService.log(u.getId(), "PARAMETRES", "Année académique en cours : " + annee.trim());
        }
    }

    /** Écrit l'année en cours après contrôle ; retourne vrai si elle a changé. */
    private boolean ecrireAnnee(String annee) throws SQLException {
        if (!Validator.anneeAcademiqueValide(annee)) {
            throw new IllegalArgumentException("Année académique invalide (format AAAA-AAAA, ex. 2025-2026).");
        }
        String avant = dao.lire(ANNEE_COURANTE);
        dao.ecrire(ANNEE_COURANTE, annee.trim());
        return !annee.trim().equals(avant);
    }

    /**
     * Ouvre ou ferme la campagne d'un niveau et fixe sa date limite (prolongation ou raccourcissement).
     * Les étudiants actifs du niveau sont notifiés. Retourne une phrase décrivant le changement, ou null.
     */
    public String definirCampagne(Niveau niveau, boolean ouverte, LocalDate dateLimite) throws SQLException {
        Utilisateur admin = exigerAdmin();
        Objects.requireNonNull(niveau, "Niveau absent.");
        if (ouverte && dateLimite != null && dateLimite.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Niveau " + niveau + " : la date limite " + Dates.format(dateLimite)
                    + " est déjà passée. Choisissez une date à venir ou fermez la campagne.");
        }
        Campagne avant = campagne(niveau);
        dao.ecrire(cleOuvert(niveau), ouverte ? "1" : "0");
        dao.ecrire(cleLimite(niveau), dateLimite == null ? "" : dateLimite.toString());

        String changement = changement(niveau, avant, ouverte, dateLimite);
        if (changement != null) {
            journalService.log(admin.getId(), "CAMPAGNE_DEPOT", changement);
            for (Utilisateur e : utilisateurDAO.findByRole(Role.ETUDIANT)) {
                if (e.isActif() && e.getNiveau() == niveau) {
                    notifications.notifier(e.getId(), "Dépôt des mémoires : " + changement);
                }
            }
        }
        return changement;
    }

    /** Décale la date limite d'un niveau de quelques jours (positif : prolonger, négatif : écourter). */
    public String decaler(Niveau niveau, int jours) throws SQLException {
        Campagne c = campagne(niveau);
        LocalDate base = c.dateLimite() == null ? LocalDate.now() : c.dateLimite();
        return definirCampagne(niveau, true, base.plusDays(jours));
    }

    private static String changement(Niveau n, Campagne avant, boolean ouverte, LocalDate limite) {
        if (!ouverte) {
            return avant.ouverte() ? "campagne fermée pour le niveau " + n + "." : null;
        }
        String date = limite == null ? "sans date limite" : "jusqu'au " + Dates.format(limite);
        if (!avant.ouverte()) {
            return "campagne ouverte pour le niveau " + n + ", " + date + ".";
        }
        if (Objects.equals(avant.dateLimite(), limite)) {
            return null;
        }
        if (avant.dateLimite() == null || limite == null) {
            return "niveau " + n + " : dépôt " + date + ".";
        }
        return "date limite du niveau " + n + (limite.isAfter(avant.dateLimite()) ? " prolongée" : " avancée")
                + " du " + Dates.format(avant.dateLimite()) + " au " + Dates.format(limite) + ".";
    }

    private static String cleOuvert(Niveau n) {
        return DEPOT_OUVERT + "." + n.name();
    }

    private static String cleLimite(Niveau n) {
        return DATE_LIMITE_DEPOT + "." + n.name();
    }

    private static Utilisateur exigerAdmin() {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null || !SessionManager.aLeRole(Role.ADMIN)) {
            throw new SecurityException("Paramètres réservés à l'administration.");
        }
        return u;
    }

    /** L'année académique commence en octobre : octobre 2026 -> « 2026-2027 », mars 2027 -> « 2026-2027 ». */
    public static String anneeParDefaut(LocalDate d) {
        int debut = d.getMonthValue() >= 10 ? d.getYear() : d.getYear() - 1;
        return debut + "-" + (debut + 1);
    }
}
