package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.PasswordUtil;
import com.tdsi.memotheque.util.Validator;

import com.tdsi.memotheque.util.SessionManager;

/**
 * [M2] Connexion, inscription « tdsien » et verrouillage de compte.
 * - inscrire(...) : matricule/email valides et uniques, mot de passe robuste, compte créé INACTIF
 * - connecter(...) : message d'échec unique et temps constant (anti-énumération),
 *   verrouillage 15 minutes après 5 échecs (levée automatique ou par l'admin),
 *   état du compte révélé seulement si le mot de passe est correct,
 *   re-hachage automatique des anciens hashes, ouverture de la session.
 *   La journalisation CONNEXION / ECHEC_CONNEXION est faite par LoginController.
 * - initialiserAdminParDefaut() : crée les comptes par défaut au premier lancement
 */
public class AuthService {

    public static final int MAX_TENTATIVES = 5;
    public static final Duration DUREE_VERROU = Duration.ofMinutes(15);

    /** Message unique pour tout échec : on ne dit pas si l'identifiant existe. */
    public static final String MESSAGE_MDP_FAIBLE =
            "Mot de passe trop faible : il faut au moins une lettre et un chiffre, "
            + "et éviter les mots de passe courants.";

    static final String MESSAGE_ECHEC = "Identifiants incorrects.";

    private static final String SEL_FACTICE = "AAAAAAAAAAAAAAAAAAAAAA==";
    private static String hashFactice;

    private final UtilisateurDAO utilisateurDAO;

    public AuthService() {
        this(new UtilisateurDAOImpl());
    }

    public AuthService(UtilisateurDAO utilisateurDAO) {
        this.utilisateurDAO = Objects.requireNonNull(utilisateurDAO);
    }

    public void inscrire(
            String matricule,
            String nom,
            String prenom,
            String email,
            String motDePasse,
            Niveau niveau) throws SQLException {

        // 1. Vérifier les champs obligatoires
        if (Validator.estVide(nom)
                || Validator.estVide(prenom)
                || Validator.estVide(email)
                || Validator.estVide(motDePasse)
                || niveau == null) {
            throw new IllegalArgumentException(
                    "Tous les champs obligatoires doivent être renseignés.");
        }

        // 2. Normaliser les données
        matricule = matricule == null
                ? null
                : matricule.trim().toUpperCase(Locale.ROOT);

        nom = nom.trim();
        prenom = prenom.trim();
        email = email.trim().toLowerCase(Locale.ROOT);

        // 3. Valider le matricule et l'email
        if (!Validator.matriculeValide(matricule)) {
            throw new IllegalArgumentException("Matricule invalide.");
        }

        if (!Validator.emailValide(email)) {
            throw new IllegalArgumentException("Adresse e-mail invalide.");
        }

        if (!Validator.longueurEntre(motDePasse, 8, 128)) {
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir entre 8 et 128 caractères.");
        }

        if (!Validator.motDePasseRobuste(motDePasse)) {
            throw new IllegalArgumentException(MESSAGE_MDP_FAIBLE);
        }

        // 4. Vérifier l'unicité
        if (utilisateurDAO.existeMatricule(matricule)) {
            throw new IllegalArgumentException(
                    "Ce matricule est déjà utilisé.");
        }

        if (utilisateurDAO.existeEmail(email)) {
            throw new IllegalArgumentException(
                    "Cette adresse e-mail est déjà utilisée.");
        }

        // 5. Générer le sel et hacher le mot de passe
        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher(motDePasse, sel);

        // 6. Construire le compte étudiant
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule(matricule);
        utilisateur.setNom(nom);
        utilisateur.setPrenom(prenom);
        utilisateur.setEmail(email);
        utilisateur.setSel(sel);
        utilisateur.setMotDePasseHash(hash);
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setNiveau(niveau);
        utilisateur.setActif(false);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        // 7. Enregistrer le compte
        utilisateurDAO.insert(utilisateur);
    }

    public Utilisateur connecter(String identifiant, String motDePasse)
            throws SQLException {

        if (Validator.estVide(identifiant)
                || Validator.estVide(motDePasse)) {
            throw new IllegalArgumentException(
                    "Identifiant et mot de passe obligatoires.");
        }

        String identifiantNormalise = identifiant.trim();

        if (identifiantNormalise.contains("@")) {
            identifiantNormalise = identifiantNormalise.toLowerCase(Locale.ROOT);
        } else {
            identifiantNormalise = identifiantNormalise.toUpperCase(Locale.ROOT);
        }

        Utilisateur utilisateur = utilisateurDAO.findByEmailOuMatricule(identifiantNormalise);

        // Compte inexistant : on calcule quand même un PBKDF2 complet pour que le temps
        // de réponse ne révèle pas si l'identifiant existe (anti-énumération).
        if (utilisateur == null) {
            PasswordUtil.verifier(motDePasse, SEL_FACTICE, hashFactice());
            throw new IllegalArgumentException(MESSAGE_ECHEC);
        }

        // Verrouillage temporaire expiré : levée automatique.
        if (utilisateur.isVerrouille() && verrouExpire(utilisateur)) {
            utilisateurDAO.mettreAJourTentatives(utilisateur.getId(), 0, false);
            utilisateur.setTentativesEchec(0);
            utilisateur.setVerrouille(false);
            utilisateur.setDateVerrouillage(null);
        }

        boolean motDePasseCorrect = PasswordUtil.verifier(
                motDePasse,
                utilisateur.getSel(),
                utilisateur.getMotDePasseHash());

        // L'état du compte (verrouillé, inactif) n'est révélé qu'à qui connaît le mot de passe.
        if (utilisateur.isVerrouille()) {
            if (motDePasseCorrect) {
                throw new IllegalArgumentException(messageVerrouille(utilisateur));
            }
            throw new IllegalArgumentException(MESSAGE_ECHEC);
        }

        if (!motDePasseCorrect) {
            int tentatives = utilisateur.getTentativesEchec() + 1;
            boolean verrouille = tentatives >= MAX_TENTATIVES;

            utilisateurDAO.mettreAJourTentatives(
                    utilisateur.getId(),
                    tentatives,
                    verrouille);

            if (verrouille) {
                throw new IllegalArgumentException(
                        "Compte verrouillé après cinq échecs. Réessayez dans "
                                + DUREE_VERROU.toMinutes() + " minutes.");
            }

            throw new IllegalArgumentException(MESSAGE_ECHEC);
        }

        // Compte inactif (mot de passe correct)
        if (!utilisateur.isActif()) {
            throw new IllegalArgumentException(
                    "Ce compte n'est pas encore activé.");
        }

        // Connexion réussie
        utilisateurDAO.mettreAJourTentatives(
                utilisateur.getId(), 0, false);

        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateVerrouillage(null);

        // Ancien format ou coût insuffisant : on recalcule le hash maintenant qu'on a le mot de passe.
        if (PasswordUtil.besoinRehash(utilisateur.getMotDePasseHash())) {
            String sel = PasswordUtil.genererSel();
            String hash = PasswordUtil.hasher(motDePasse, sel);
            if (!utilisateur.isDoitChangerMotDePasse()) {
                utilisateurDAO.changerMotDePasse(utilisateur.getId(), hash, sel);
                utilisateur.setSel(sel);
                utilisateur.setMotDePasseHash(hash);
            }
        }

        SessionManager.ouvrir(utilisateur);

        return utilisateur;
    }

    private static boolean verrouExpire(Utilisateur u) {
        // Sans date (ancien verrou ou verrou posé à la main) : seul l'admin peut déverrouiller.
        return u.getDateVerrouillage() != null
                && !LocalDateTime.now().isBefore(u.getDateVerrouillage().plus(DUREE_VERROU));
    }

    private static String messageVerrouille(Utilisateur u) {
        if (u.getDateVerrouillage() == null) {
            return "Ce compte est verrouillé.";
        }
        long minutes = Math.max(1, Duration.between(LocalDateTime.now(),
                u.getDateVerrouillage().plus(DUREE_VERROU)).toMinutes() + 1);
        return "Ce compte est verrouillé. Réessayez dans " + minutes
                + " minute(s) ou contactez l'administrateur.";
    }

    private static synchronized String hashFactice() {
        if (hashFactice == null) {
            hashFactice = PasswordUtil.hasher("compte-inexistant", SEL_FACTICE);
        }
        return hashFactice;
    }

    public void initialiserAdminParDefaut() throws SQLException {
        creerCompteDemoSiAbsent(
                null, "Administrateur", "MémoTDSI",
                "admin@memotdsi.local",
                System.getenv("MEMOTDSI_ADMIN_PASSWORD"),
                Role.ADMIN, null);

        creerCompteDemoSiAbsent(
                null, "Encadreur", "Démo",
                "encadreur.demo@memotdsi.local",
                System.getenv("MEMOTDSI_ENCADREUR_PASSWORD"),
                Role.ENCADREUR, null);

        creerCompteDemoSiAbsent(
                "TDSI-DEMO-001", "Étudiant", "Démo 1",
                "etudiant1.demo@memotdsi.local",
                System.getenv("MEMOTDSI_ETUDIANT1_PASSWORD"),
                Role.ETUDIANT, Niveau.L3);

        creerCompteDemoSiAbsent(
                "TDSI-DEMO-002", "Étudiant", "Démo 2",
                "etudiant2.demo@memotdsi.local",
                System.getenv("MEMOTDSI_ETUDIANT2_PASSWORD"),
                Role.ETUDIANT, Niveau.M2);
    }

    private void creerCompteDemoSiAbsent(
            String matricule,
            String nom,
            String prenom,
            String email,
            String motDePasse,
            Role role,
            Niveau niveau) throws SQLException {

        if (utilisateurDAO.existeEmail(email)
                || (matricule != null
                        && utilisateurDAO.existeMatricule(matricule))) {
            return;
        }

        if (!Validator.longueurEntre(motDePasse, 8, 128)) {
            throw new IllegalStateException(
                    "Mot de passe de démonstration absent ou trop court pour : "
                            + email);
        }

        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher(motDePasse, sel);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule(matricule);
        utilisateur.setNom(nom);
        utilisateur.setPrenom(prenom);
        utilisateur.setEmail(email);
        utilisateur.setMotDePasseHash(hash);
        utilisateur.setSel(sel);
        utilisateur.setRole(role);
        utilisateur.setNiveau(niveau);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        utilisateurDAO.insert(utilisateur);
    }

}
