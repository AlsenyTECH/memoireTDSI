package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.PasswordUtil;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Validator;

/**
 * [M6] Gestion des comptes (réservé à l'ADMIN : vérifier SessionManager.aLeRole(Role.ADMIN)).
 * À faire : activer / désactiver / déverrouiller un compte, créer un compte encadreur, changer son mot de passe.
 *
 * changerMotDePasse(...) est la seule méthode ouverte à tout utilisateur connecté (pour son propre compte).
 */
public class CompteService {

    private final UtilisateurDAO utilisateurDAO;
    private final JournalService journalService;

    public CompteService() {
        this(new UtilisateurDAOImpl(), new JournalService());
    }

    // Constructeur utile pour les tests.
    public CompteService(UtilisateurDAO utilisateurDAO, JournalService journalService) {
        this.utilisateurDAO = Objects.requireNonNull(utilisateurDAO);
        this.journalService = Objects.requireNonNull(journalService);
    }

    public List<Utilisateur> listerComptes() throws SQLException {
        exigerAdmin();
        return utilisateurDAO.findAll();
    }

    public void activer(int idCompte) throws SQLException {
        Utilisateur admin = exigerAdmin();
        Utilisateur cible = charger(idCompte);

        utilisateurDAO.changerActivation(idCompte, true);
        journalService.log(admin.getId(), "ACTIVATION_COMPTE", "Compte #" + idCompte + " " + cible.getEmail());
        new NotificationService().notifier(idCompte, "Bienvenue sur MémoTDSI ! Votre compte a été activé.");
    }

    public void desactiver(int idCompte) throws SQLException {
        Utilisateur admin = exigerAdmin();
        if (idCompte == admin.getId()) {
            throw new IllegalArgumentException("Vous ne pouvez pas désactiver votre propre compte.");
        }
        Utilisateur cible = charger(idCompte);

        utilisateurDAO.changerActivation(idCompte, false);
        journalService.log(admin.getId(), "DESACTIVATION_COMPTE", "Compte #" + idCompte + " " + cible.getEmail());
    }

    /** Remet le compteur d'échecs à zéro et lève le verrouillage. */
    public void deverrouiller(int idCompte) throws SQLException {
        Utilisateur admin = exigerAdmin();
        Utilisateur cible = charger(idCompte);

        utilisateurDAO.mettreAJourTentatives(idCompte, 0, false);
        journalService.log(admin.getId(), "DEVERROUILLAGE_COMPTE", "Compte #" + idCompte + " " + cible.getEmail());
    }

    /** Crée un compte encadreur actif (les encadreurs ne s'inscrivent pas eux-mêmes). */
    public Utilisateur creerEncadreur(String nom, String prenom, String email, String motDePasse)
            throws SQLException {
        Utilisateur admin = exigerAdmin();

        if (Validator.estVide(nom) || Validator.estVide(prenom)
                || Validator.estVide(email) || Validator.estVide(motDePasse)) {
            throw new IllegalArgumentException("Tous les champs sont obligatoires.");
        }

        String emailNormalise = email.trim().toLowerCase(Locale.ROOT);
        if (!Validator.emailValide(emailNormalise)) {
            throw new IllegalArgumentException("Adresse e-mail invalide.");
        }
        if (!Validator.longueurEntre(nom, 1, 80) || !Validator.longueurEntre(prenom, 1, 80)) {
            throw new IllegalArgumentException("Nom et prénom : 80 caractères maximum.");
        }
        verifierMotDePasse(motDePasse);
        if (utilisateurDAO.existeEmail(emailNormalise)) {
            throw new IllegalArgumentException("Cette adresse e-mail est déjà utilisée.");
        }

        String sel = PasswordUtil.genererSel();

        Utilisateur u = new Utilisateur();
        u.setNom(nom.trim());
        u.setPrenom(prenom.trim());
        u.setEmail(emailNormalise);
        u.setSel(sel);
        u.setMotDePasseHash(PasswordUtil.hasher(motDePasse, sel));
        u.setRole(Role.ENCADREUR);
        u.setActif(true);
        u.setTentativesEchec(0);
        u.setVerrouille(false);
        u.setDateCreation(LocalDate.now());

        utilisateurDAO.insert(u);
        journalService.log(admin.getId(), "CREATION_COMPTE",
                "Encadreur #" + u.getId() + " " + emailNormalise);
        return u;
    }

    /**
     * L'admin pose un mot de passe temporaire (compte oublié ou compromis).
     * Le compte est déverrouillé et devra changer ce mot de passe à la prochaine connexion.
     * Le mot de passe temporaire est retourné une seule fois pour être communiqué à l'utilisateur.
     */
    public String reinitialiserMotDePasse(int idCompte) throws SQLException {
        Utilisateur admin = exigerAdmin();
        if (idCompte == admin.getId()) {
            throw new IllegalArgumentException("Changez votre propre mot de passe depuis « Mon profil ».");
        }
        Utilisateur cible = charger(idCompte);

        String temporaire = PasswordUtil.genererMotDePasseTemporaire();
        String sel = PasswordUtil.genererSel();
        utilisateurDAO.reinitialiserMotDePasse(idCompte, PasswordUtil.hasher(temporaire, sel), sel);

        // L'ancienne clé privée de signature (chiffrée par l'ancien mot de passe) devient inutilisable.
        new SignatureService().revoquer(idCompte);
        journalService.log(admin.getId(), "REINITIALISATION_MOT_DE_PASSE",
                "Compte #" + idCompte + " " + cible.getEmail());
        new NotificationService().notifier(idCompte, "Votre mot de passe a été réinitialisé par l'administrateur "
                + admin.getNomComplet() + ". Si vous n'êtes pas à l'origine de la demande, signalez-le.");
        return temporaire;
    }

    /** L'utilisateur connecté change SON mot de passe (l'ancien est exigé). */
    public void changerMotDePasse(String ancien, String nouveau) throws SQLException {
        Utilisateur session = SessionManager.getUtilisateur();
        if (session == null) {
            throw new SecurityException("Vous devez être connecté.");
        }
        if (Validator.estVide(ancien) || Validator.estVide(nouveau)) {
            throw new IllegalArgumentException("Ancien et nouveau mots de passe obligatoires.");
        }

        Utilisateur u = charger(session.getId());
        if (!PasswordUtil.verifier(ancien, u.getSel(), u.getMotDePasseHash())) {
            journalService.log(u.getId(), "ECHEC_CHANGEMENT_MOT_DE_PASSE", "Ancien mot de passe incorrect");
            throw new IllegalArgumentException("Ancien mot de passe incorrect.");
        }
        verifierMotDePasse(nouveau);
        if (ancien.equals(nouveau)) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'ancien.");
        }

        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher(nouveau, sel);
        utilisateurDAO.changerMotDePasse(u.getId(), hash, sel);
        // La clé privée de signature suit le mot de passe (re-chiffrement).
        new SignatureService().rechiffrer(u.getId(), ancien, nouveau);

        session.setSel(sel);
        session.setMotDePasseHash(hash);
        session.setDoitChangerMotDePasse(false);
        journalService.log(u.getId(), "CHANGEMENT_MOT_DE_PASSE", u.getEmail());
    }

    private void verifierMotDePasse(String motDePasse) {
        // Même règles que l'inscription (AuthService).
        if (!Validator.longueurEntre(motDePasse, 8, 128)) {
            throw new IllegalArgumentException("Le mot de passe doit contenir entre 8 et 128 caractères.");
        }
        if (!Validator.motDePasseRobuste(motDePasse)) {
            throw new IllegalArgumentException(AuthService.MESSAGE_MDP_FAIBLE);
        }
    }

    private Utilisateur charger(int id) throws SQLException {
        Utilisateur u = utilisateurDAO.findById(id);
        if (u == null) {
            throw new IllegalArgumentException("Compte introuvable.");
        }
        return u;
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
