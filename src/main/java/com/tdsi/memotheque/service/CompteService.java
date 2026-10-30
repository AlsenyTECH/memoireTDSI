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

    /** Résultat d'une ligne d'import : CREE (avec mot de passe temporaire), REINSCRIT ou ERREUR. */
    public record LigneImport(int numero, String identifiant, String resultat, String detail) { }

    public static final int IMPORT_MAX_LIGNES = 2000;

    /** Lit le fichier CSV (UTF-8) exporté du tableur de la scolarité. */
    public List<LigneImport> importerEtudiants(java.nio.file.Path csv) throws SQLException, java.io.IOException {
        exigerAdmin();
        if (java.nio.file.Files.size(csv) > 1_000_000) {
            throw new IllegalArgumentException("Fichier trop volumineux (1 Mo maximum).");
        }
        return importerEtudiants(java.nio.file.Files.readAllLines(csv, java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Inscription en masse par la scolarité, comme dans un système d'information universitaire :
     * une ligne « matricule;nom;prénom;email;niveau » par étudiant (séparateur ; ou ,, en-tête facultatif).
     * Nouvel étudiant : compte actif, mot de passe temporaire à changer à la première connexion.
     * Étudiant déjà inscrit (même matricule et même e-mail) : réinscription, son niveau est mis à jour.
     * Chaque ligne est traitée indépendamment : une ligne invalide n'empêche pas les autres.
     */
    public List<LigneImport> importerEtudiants(List<String> lignes) throws SQLException {
        Utilisateur admin = exigerAdmin();
        if (lignes.size() > IMPORT_MAX_LIGNES + 1) {
            throw new IllegalArgumentException("Import limité à " + IMPORT_MAX_LIGNES + " étudiants par fichier.");
        }
        List<LigneImport> resultats = new java.util.ArrayList<>();
        java.util.Set<String> vus = new java.util.HashSet<>();
        int crees = 0;
        int reinscrits = 0;
        for (int i = 0; i < lignes.size(); i++) {
            String brute = lignes.get(i).replace("\uFEFF", "").trim();
            if (brute.isEmpty() || (i == 0 && brute.toLowerCase(Locale.ROOT).startsWith("matricule"))) {
                continue;
            }
            String[] c = brute.split(brute.contains(";") ? ";" : ",", -1);
            String id = c.length > 3 ? c[3].trim() : brute;
            try {
                if (c.length != 5) {
                    throw new IllegalArgumentException("5 colonnes attendues : matricule;nom;prénom;email;niveau.");
                }
                String matricule = c[0].trim().toUpperCase(Locale.ROOT);
                String nom = c[1].trim();
                String prenom = c[2].trim();
                String email = c[3].trim().toLowerCase(Locale.ROOT);
                com.tdsi.memotheque.model.Niveau niveau;
                try {
                    niveau = com.tdsi.memotheque.model.Niveau.valueOf(c[4].trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Niveau inconnu « " + c[4].trim() + " » (L1, L2, L3, M1 ou M2).");
                }
                if (!Validator.matriculeValide(matricule)) {
                    throw new IllegalArgumentException("Matricule invalide.");
                }
                if (!Validator.emailValide(email)) {
                    throw new IllegalArgumentException("Adresse e-mail invalide.");
                }
                if (!Validator.longueurEntre(nom, 1, 80) || !Validator.longueurEntre(prenom, 1, 80)) {
                    throw new IllegalArgumentException("Nom et prénom obligatoires (80 caractères maximum).");
                }
                if (!vus.add(email) || !vus.add("#" + matricule)) {
                    throw new IllegalArgumentException("Doublon dans le fichier.");
                }

                Utilisateur parEmail = utilisateurDAO.existeEmail(email) ? utilisateurDAO.findByEmailOuMatricule(email) : null;
                boolean matriculePris = utilisateurDAO.existeMatricule(matricule);
                if (parEmail != null || matriculePris) {
                    if (parEmail == null || parEmail.getRole() != Role.ETUDIANT
                            || !matricule.equals(parEmail.getMatricule())) {
                        throw new IllegalArgumentException("E-mail ou matricule déjà utilisé par un autre compte.");
                    }
                    parEmail.setNiveau(niveau);
                    utilisateurDAO.update(parEmail);
                    reinscrits++;
                    resultats.add(new LigneImport(i + 1, email, "REINSCRIT", "Niveau " + niveau));
                    continue;
                }

                String temporaire = PasswordUtil.genererMotDePasseTemporaire();
                String sel = PasswordUtil.genererSel();
                Utilisateur u = new Utilisateur();
                u.setMatricule(matricule);
                u.setNom(nom);
                u.setPrenom(prenom);
                u.setEmail(email);
                u.setSel(sel);
                u.setMotDePasseHash(PasswordUtil.hasher(temporaire, sel));
                u.setRole(Role.ETUDIANT);
                u.setNiveau(niveau);
                u.setActif(true);
                u.setDoitChangerMotDePasse(true);
                u.setDateCreation(LocalDate.now());
                utilisateurDAO.insert(u);
                crees++;
                resultats.add(new LigneImport(i + 1, email, "CREE", temporaire));
            } catch (IllegalArgumentException e) {
                resultats.add(new LigneImport(i + 1, id, "ERREUR", e.getMessage()));
            }
        }
        journalService.log(admin.getId(), "IMPORT_ETUDIANTS", crees + " compte(s) créé(s), " + reinscrits
                + " réinscription(s), " + (resultats.size() - crees - reinscrits) + " ligne(s) en erreur");
        return resultats;
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
