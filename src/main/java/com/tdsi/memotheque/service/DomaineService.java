package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import com.tdsi.memotheque.dao.DomaineDAO;
import com.tdsi.memotheque.dao.impl.DomaineDAOImpl;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Validator;

/**
 * [M6] Gestion des domaines (ajout, modification, suppression si non utilisé).
 * Lecture : tout le monde. Écriture : ADMIN uniquement, avec journalisation.
 */
public class DomaineService {

    public static final int NOM_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;

    private final DomaineDAO domaineDAO;
    private final JournalService journalService;

    public DomaineService() {
        this(new DomaineDAOImpl(), new JournalService());
    }

    // Constructeur utile pour les tests.
    public DomaineService(DomaineDAO domaineDAO, JournalService journalService) {
        this.domaineDAO = Objects.requireNonNull(domaineDAO);
        this.journalService = Objects.requireNonNull(journalService);
    }

    public List<Domaine> lister() throws SQLException {
        return domaineDAO.findAll();
    }

    public void ajouter(String nom, String description) throws SQLException {
        Utilisateur admin = exigerAdmin();
        Domaine d = new Domaine(0, nom, description);
        valider(d);

        try {
            domaineDAO.insert(d);
        } catch (SQLException e) {
            throw traduireDoublon(e);
        }
        journalService.log(admin.getId(), "AJOUT_DOMAINE", d.getNom());
    }

    public void modifier(int id, String nom, String description) throws SQLException {
        Utilisateur admin = exigerAdmin();
        Domaine existant = domaineDAO.findById(id);
        if (existant == null) {
            throw new IllegalArgumentException("Domaine introuvable.");
        }

        Domaine d = new Domaine(id, nom, description);
        valider(d);

        try {
            domaineDAO.update(d);
        } catch (SQLException e) {
            throw traduireDoublon(e);
        }
        journalService.log(admin.getId(), "MODIFICATION_DOMAINE",
                "#" + id + " " + existant.getNom() + " -> " + d.getNom());
    }

    public void supprimer(int id) throws SQLException {
        Utilisateur admin = exigerAdmin();
        Domaine existant = domaineDAO.findById(id);
        if (existant == null) {
            throw new IllegalArgumentException("Domaine introuvable.");
        }
        if (domaineDAO.estUtilise(id)) {
            throw new IllegalStateException(
                    "Ce domaine est utilisé par au moins un mémoire : suppression impossible.");
        }

        domaineDAO.delete(id);
        journalService.log(admin.getId(), "SUPPRESSION_DOMAINE", "#" + id + " " + existant.getNom());
    }

    private void valider(Domaine d) {
        if (!Validator.longueurEntre(d.getNom(), 2, NOM_MAX)) {
            throw new IllegalArgumentException("Le nom du domaine doit contenir entre 2 et "
                    + NOM_MAX + " caractères.");
        }
        d.setNom(d.getNom().trim());

        String description = d.getDescription() == null || d.getDescription().isBlank()
                ? null : d.getDescription().trim();
        if (description != null && description.length() > DESCRIPTION_MAX) {
            throw new IllegalArgumentException("La description ne doit pas dépasser "
                    + DESCRIPTION_MAX + " caractères.");
        }
        d.setDescription(description);
    }

    /** Le nom est UNIQUE dans le schéma : message clair au lieu de l'erreur SQLite. */
    private SQLException traduireDoublon(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("UNIQUE")) {
            throw new IllegalArgumentException("Un domaine porte déjà ce nom.");
        }
        return e;
    }

    private Utilisateur exigerAdmin() {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null) {
            throw new SecurityException("Vous devez être connecté.");
        }
        if (!SessionManager.aLeRole(Role.ADMIN)) {
            throw new SecurityException("Action réservée à l'administrateur.");
        }
        return u;
    }
}
