package com.tdsi.memotheque.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.tdsi.memotheque.dao.MemoireDAO;
import com.tdsi.memotheque.dao.ValidationDAO;
import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.ValidationDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.SessionManager;

/**
 * [M5] Évaluation des mémoires soumis par l'encadreur (première étape du circuit) :
 * avis favorable (autorisé à soutenir) ou corrections demandées (motif obligatoire).
 * Un encadreur n'évalue que SES mémoires ; l'administration peut tout évaluer.
 * La publication (après soutenance) relève de SoutenanceService.
 *
 * Le mémoire est toujours relu en base : on ne fait pas confiance à l'objet affiché à l'écran.
 */
public class ValidationService {

    public static final int MOTIF_MIN = 5;
    public static final int MOTIF_MAX = 500;

    private final ValidationDAO validationDAO;
    private final MemoireDAO memoireDAO;
    private final JournalService journalService;
    private final FichierService fichierService;
    private final NotificationService notifications = new NotificationService();

    public ValidationService() {
        this(new ValidationDAOImpl(), new MemoireDAOImpl(), new JournalService(), new FichierService());
    }

    // Constructeur utile pour les tests.
    public ValidationService(ValidationDAO validationDAO, MemoireDAO memoireDAO,
            JournalService journalService, FichierService fichierService) {
        this.validationDAO = Objects.requireNonNull(validationDAO);
        this.memoireDAO = Objects.requireNonNull(memoireDAO);
        this.journalService = Objects.requireNonNull(journalService);
        this.fichierService = Objects.requireNonNull(fichierService);
    }

    /** Encadreur : ses mémoires en attente. Admin : tous les mémoires en attente. */
    public List<Memoire> listerEnAttente() throws SQLException {
        Utilisateur u = exigerValidateur();
        return validationDAO.findEnAttente(u.getRole() == Role.ADMIN ? null : u.getId());
    }

    /**
     * Avis favorable de l'encadreur : le mémoire est autorisé à être soutenu.
     * La scolarité est prévenue pour planifier la soutenance. Le PDF doit être intègre.
     */
    public void donnerAvisFavorable(int idMemoire) throws SQLException, IOException {
        Utilisateur u = exigerValidateur();
        Memoire m = chargerTraitable(idMemoire, u);

        // On n'autorise pas la soutenance d'un fichier dont l'intégrité n'est pas vérifiée.
        fichierService.lireContenuVerifie(m);

        validationDAO.autoriser(idMemoire, LocalDate.now());
        journalService.log(u.getId(), "AVIS_FAVORABLE",
                "Mémoire #" + idMemoire + " « " + m.getTitre() + " » autorisé à soutenir");
        notifications.notifier(m.getAuteur().getId(), "Bonne nouvelle : votre mémoire « " + m.getTitre()
                + " » est autorisé à être soutenu. La scolarité va planifier votre soutenance.");
        notifications.notifierAdmins("Soutenance à planifier : « " + m.getTitre() + " » ("
                + m.getAuteurNomComplet() + ", " + m.getNiveau() + "), avis favorable de " + u.getNomComplet() + ".");
    }

    public void refuser(int idMemoire, String motif) throws SQLException {
        Utilisateur u = exigerValidateur();

        String motifNettoye = motif == null ? "" : motif.trim();
        if (motifNettoye.length() < MOTIF_MIN || motifNettoye.length() > MOTIF_MAX) {
            throw new IllegalArgumentException("Le motif (corrections demandées) est obligatoire ("
                    + MOTIF_MIN + " à " + MOTIF_MAX + " caractères).");
        }

        Memoire m = chargerTraitable(idMemoire, u);

        validationDAO.refuser(idMemoire, u.getId(), motifNettoye);
        journalService.log(u.getId(), "REFUS",
                "Mémoire #" + idMemoire + " « " + m.getTitre() + " » : " + motifNettoye);
        notifications.notifier(m.getAuteur().getId(), "Corrections demandées sur « " + m.getTitre()
                + " » : " + motifNettoye + " — corrigez-le puis re-soumettez-le depuis « Mes mémoires ».");
    }

    /** Retourne le PDF à lire avant décision, après contrôle des droits et de l'intégrité. */
    public Path preparerLecture(int idMemoire) throws SQLException, IOException {
        Utilisateur u = exigerValidateur();
        Memoire m = chargerTraitable(idMemoire, u);
        return verifierIntegrite(m);
    }

    private Memoire chargerTraitable(int idMemoire, Utilisateur u) throws SQLException {
        Memoire m = memoireDAO.findById(idMemoire);

        if (m == null) {
            throw new IllegalArgumentException("Mémoire introuvable.");
        }
        if (m.getStatut() != StatutMemoire.EN_ATTENTE) {
            throw new IllegalStateException("Ce mémoire a déjà été évalué.");
        }
        if (u.getRole() == Role.ENCADREUR
                && (m.getEncadreur() == null || m.getEncadreur().getId() != u.getId())) {
            throw new SecurityException("Vous ne pouvez traiter que les mémoires que vous encadrez.");
        }
        return m;
    }

    /** Déchiffre et contrôle le PDF (tag AES-GCM + SHA-256) vers un fichier temporaire privé. */
    private Path verifierIntegrite(Memoire m) throws IOException {
        return fichierService.extraireVersTemporaire(m);
    }

    private Utilisateur exigerValidateur() {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null) {
            throw new SecurityException("Vous devez être connecté.");
        }
        if (!SessionManager.aLeRole(Role.ENCADREUR, Role.ADMIN)) {
            throw new SecurityException("Action réservée aux encadreurs et à l'administrateur.");
        }
        return u;
    }
}
