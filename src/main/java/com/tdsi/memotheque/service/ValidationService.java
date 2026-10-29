package com.tdsi.memotheque.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.tdsi.memotheque.dao.MemoireDAO;
import com.tdsi.memotheque.dao.ValidationDAO;
import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.ValidationDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.HashUtil;
import com.tdsi.memotheque.util.SessionManager;

/**
 * [M5] Validation / refus des mémoires.
 * À faire : un encadreur ne valide que SES mémoires (l'admin peut tout valider), motif obligatoire pour un refus,
 * renseigner date_publication et id_validateur, journaliser VALIDATION / REFUS.
 *
 * Le mémoire est toujours relu en base : on ne fait pas confiance à l'objet affiché à l'écran.
 */
public class ValidationService {

    public static final int MOTIF_MIN = 5;
    public static final int MOTIF_MAX = 500;

    private final ValidationDAO validationDAO;
    private final MemoireDAO memoireDAO;
    private final JournalService journalService;

    public ValidationService() {
        this(new ValidationDAOImpl(), new MemoireDAOImpl(), new JournalService());
    }

    // Constructeur utile pour les tests.
    public ValidationService(ValidationDAO validationDAO, MemoireDAO memoireDAO,
            JournalService journalService) {
        this.validationDAO = Objects.requireNonNull(validationDAO);
        this.memoireDAO = Objects.requireNonNull(memoireDAO);
        this.journalService = Objects.requireNonNull(journalService);
    }

    /** Encadreur : ses mémoires en attente. Admin : tous les mémoires en attente. */
    public List<Memoire> listerEnAttente() throws SQLException {
        Utilisateur u = exigerValidateur();
        return validationDAO.findEnAttente(u.getRole() == Role.ADMIN ? null : u.getId());
    }

    public void valider(int idMemoire) throws SQLException, IOException {
        Utilisateur u = exigerValidateur();
        Memoire m = chargerTraitable(idMemoire, u);

        // On ne publie pas un fichier dont l'intégrité n'est pas vérifiée.
        verifierIntegrite(m);

        validationDAO.valider(idMemoire, u.getId());
        journalService.log(u.getId(), "VALIDATION",
                "Mémoire #" + idMemoire + " « " + m.getTitre() + " »");
    }

    public void refuser(int idMemoire, String motif) throws SQLException {
        Utilisateur u = exigerValidateur();

        String motifNettoye = motif == null ? "" : motif.trim();
        if (motifNettoye.length() < MOTIF_MIN || motifNettoye.length() > MOTIF_MAX) {
            throw new IllegalArgumentException("Le motif du refus est obligatoire ("
                    + MOTIF_MIN + " à " + MOTIF_MAX + " caractères).");
        }

        Memoire m = chargerTraitable(idMemoire, u);

        validationDAO.refuser(idMemoire, u.getId(), motifNettoye);
        journalService.log(u.getId(), "REFUS",
                "Mémoire #" + idMemoire + " « " + m.getTitre() + " » : " + motifNettoye);
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
            throw new IllegalStateException("Ce mémoire a déjà été traité.");
        }
        if (u.getRole() == Role.ENCADREUR
                && (m.getEncadreur() == null || m.getEncadreur().getId() != u.getId())) {
            throw new SecurityException("Vous ne pouvez traiter que les mémoires que vous encadrez.");
        }
        return m;
    }

    private Path verifierIntegrite(Memoire m) throws IOException {
        if (m.getCheminFichier() == null || m.getCheminFichier().isBlank()) {
            throw new IOException("Le chemin du PDF est absent.");
        }

        Path fichier = Path.of(m.getCheminFichier()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(fichier) || !Files.isReadable(fichier)) {
            throw new IOException("Le fichier PDF est absent ou illisible.");
        }

        String attendu = m.getEmpreinteSha256() == null ? ""
                : m.getEmpreinteSha256().trim().toLowerCase(Locale.ROOT);
        String actuel = HashUtil.sha256(fichier);

        if (!MessageDigest.isEqual(attendu.getBytes(StandardCharsets.US_ASCII),
                actuel.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException(
                    "L'intégrité du PDF n'est pas vérifiée : le fichier a été modifié depuis le dépôt.");
        }
        return fichier;
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
