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
    private final FichierService fichierService;
    private final SignatureService signatureService;

    public ValidationService() {
        this(new ValidationDAOImpl(), new MemoireDAOImpl(), new JournalService(), new FichierService(),
                new SignatureService());
    }

    // Constructeur utile pour les tests.
    public ValidationService(ValidationDAO validationDAO, MemoireDAO memoireDAO,
            JournalService journalService, FichierService fichierService, SignatureService signatureService) {
        this.validationDAO = Objects.requireNonNull(validationDAO);
        this.memoireDAO = Objects.requireNonNull(memoireDAO);
        this.journalService = Objects.requireNonNull(journalService);
        this.fichierService = Objects.requireNonNull(fichierService);
        this.signatureService = Objects.requireNonNull(signatureService);
    }

    /** Encadreur : ses mémoires en attente. Admin : tous les mémoires en attente. */
    public List<Memoire> listerEnAttente() throws SQLException {
        Utilisateur u = exigerValidateur();
        return validationDAO.findEnAttente(u.getRole() == Role.ADMIN ? null : u.getId());
    }

    /**
     * Valide, publie et SIGNE (Ed25519) un mémoire. Le mot de passe du validateur est exigé :
     * il re-confirme son identité et déchiffre sa clé privée de signature.
     */
    public void valider(int idMemoire, String motDePasse) throws SQLException, IOException {
        Utilisateur u = exigerValidateur();
        if (motDePasse == null || motDePasse.isEmpty()) {
            throw new IllegalArgumentException("Confirmez votre mot de passe pour signer la validation.");
        }
        Memoire m = chargerTraitable(idMemoire, u);

        // On ne publie pas un fichier dont l'intégrité n'est pas vérifiée (tag GCM + SHA-256).
        fichierService.lireContenuVerifie(m);

        LocalDate datePublication = LocalDate.now();
        SignatureService.SignatureCalculee signature;
        try {
            signature = signatureService.signer(u, motDePasse, m, datePublication);
        } catch (SecurityException e) {
            journalService.log(u.getId(), "ECHEC_SIGNATURE", "Mémoire #" + idMemoire + " : mot de passe incorrect");
            throw e;
        }

        validationDAO.validerEtSigner(idMemoire, u.getId(), datePublication,
                signature.signature(), signature.idCle());
        journalService.log(u.getId(), "VALIDATION",
                "Mémoire #" + idMemoire + " « " + m.getTitre() + " » signé (clé Ed25519 "
                        + signature.empreinteCle() + ")");
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
