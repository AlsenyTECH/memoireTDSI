package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.time.LocalDate;
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
 * À faire :
 * - inscrire(...) : valider matricule/email, email unique, compte créé INACTIF
 * - connecter(...) : refuser si inactif ou verrouillé, verrouiller après 5
 * échecs, remettre le compteur à 0 si succès,
 * ouvrir la session (SessionManager.ouvrir), journaliser CONNEXION /
 * ECHEC_CONNEXION
 * - initialiserAdminParDefaut() : crée le compte admin au premier lancement
 * (mot de passe haché avec PasswordUtil)
 */
public class AuthService {
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

        // Compte inexistant
        if (utilisateur == null) {
            // POINT D'INTEGRATION M6 :
            // Journaliser ECHEC_CONNEXION sans identifiant utilisateur.
            throw new IllegalArgumentException(
                    "Identifiants incorrects.");
        }

        // Compte inactif
        if (!utilisateur.isActif()) {
            throw new IllegalArgumentException(
                    "Ce compte n'est pas encore activé.");
        }

        // Compte verrouillé
        if (utilisateur.isVerrouille()) {
            throw new IllegalArgumentException(
                    "Ce compte est verrouillé.");
        }

        boolean motDePasseCorrect = PasswordUtil.verifier(
                motDePasse,
                utilisateur.getSel(),
                utilisateur.getMotDePasseHash());

        if (!motDePasseCorrect) {
            int tentatives = utilisateur.getTentativesEchec() + 1;
            boolean verrouille = tentatives >= 5;

            utilisateurDAO.mettreAJourTentatives(
                    utilisateur.getId(),
                    tentatives,
                    verrouille);

            // POINT D'INTEGRATION M6 :
            // Journaliser ECHEC_CONNEXION avec l'ID utilisateur.

            if (verrouille) {
                throw new IllegalArgumentException(
                        "Compte verrouillé après cinq échecs.");
            }

            throw new IllegalArgumentException(
                    "Identifiants incorrects. Tentative "
                            + tentatives + " sur 5.");
        }

        // Connexion réussie
        utilisateurDAO.mettreAJourTentatives(
                utilisateur.getId(), 0, false);

        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);

        SessionManager.ouvrir(utilisateur);

        // POINT D'INTEGRATION M6 :
        // Journaliser CONNEXION avec l'ID utilisateur.

        return utilisateur;
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
