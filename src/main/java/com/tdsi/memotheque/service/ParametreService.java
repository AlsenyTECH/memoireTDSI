package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

import com.tdsi.memotheque.dao.ParametreDAO;
import com.tdsi.memotheque.dao.impl.ParametreDAOImpl;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Validator;

/**
 * Paramètres de la scolarité : année académique en cours et campagne de dépôt
 * (ouverte / fermée, date limite). C'est l'administration, pas l'étudiant, qui fixe l'année.
 */
public class ParametreService {

    public static final String ANNEE_COURANTE = "annee_courante";
    public static final String DEPOT_OUVERT = "depot_ouvert";
    public static final String DATE_LIMITE_DEPOT = "date_limite_depot";

    /** État de la campagne, pour l'affichage. */
    public record Campagne(String anneeCourante, boolean ouverte, LocalDate dateLimite) {
        public boolean depotPossible(LocalDate aujourdhui) {
            return ouverte && (dateLimite == null || !aujourdhui.isAfter(dateLimite));
        }
    }

    private final ParametreDAO dao;
    private final JournalService journalService;

    public ParametreService() {
        this(new ParametreDAOImpl(), new JournalService());
    }

    public ParametreService(ParametreDAO dao, JournalService journalService) {
        this.dao = Objects.requireNonNull(dao);
        this.journalService = Objects.requireNonNull(journalService);
    }

    public Campagne campagne() throws SQLException {
        String annee = dao.lire(ANNEE_COURANTE);
        String limite = dao.lire(DATE_LIMITE_DEPOT);
        return new Campagne(
                Validator.anneeAcademiqueValide(annee) ? annee : anneeParDefaut(LocalDate.now()),
                !"0".equals(dao.lire(DEPOT_OUVERT)),
                limite == null || limite.isBlank() ? null : LocalDate.parse(limite));
    }

    public String anneeCourante() throws SQLException {
        return campagne().anneeCourante();
    }

    /** Refuse un dépôt hors campagne (fermée ou date limite dépassée). */
    public void verifierDepotPossible() throws SQLException {
        Campagne c = campagne();
        if (!c.ouverte()) {
            throw new IllegalStateException("La campagne de dépôt est fermée par la scolarité.");
        }
        if (c.dateLimite() != null && LocalDate.now().isAfter(c.dateLimite())) {
            throw new IllegalStateException("La date limite de dépôt (" + c.dateLimite()
                    + ") est dépassée. Contactez la scolarité.");
        }
    }

    /** Réservé à l'administration. */
    public void definir(String annee, boolean ouverte, LocalDate dateLimite) throws SQLException {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null || !SessionManager.aLeRole(Role.ADMIN)) {
            throw new SecurityException("Paramètres réservés à l'administration.");
        }
        if (!Validator.anneeAcademiqueValide(annee)) {
            throw new IllegalArgumentException("Année académique invalide (format AAAA-AAAA, ex. 2025-2026).");
        }
        dao.ecrire(ANNEE_COURANTE, annee.trim());
        dao.ecrire(DEPOT_OUVERT, ouverte ? "1" : "0");
        dao.ecrire(DATE_LIMITE_DEPOT, dateLimite == null ? null : dateLimite.toString());
        journalService.log(u.getId(), "PARAMETRES", "Année " + annee.trim() + ", dépôt "
                + (ouverte ? "ouvert" : "fermé") + (dateLimite == null ? "" : " jusqu'au " + dateLimite));
    }

    /** L'année académique commence en octobre : octobre 2026 -> « 2026-2027 », mars 2027 -> « 2026-2027 ». */
    public static String anneeParDefaut(LocalDate d) {
        int debut = d.getMonthValue() >= 10 ? d.getYear() : d.getYear() - 1;
        return debut + "-" + (debut + 1);
    }
}
