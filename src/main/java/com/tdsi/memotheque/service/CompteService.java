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
import com.tdsi.memotheque.util.Tableur;
import com.tdsi.memotheque.model.Niveau;
import java.util.Map;
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

    /** Résultat d'une ligne d'import : CREE (avec mot de passe temporaire), REINSCRIT / MIS_A_JOUR ou ERREUR. */
    public record LigneImport(int numero, String identifiant, String resultat, String detail) { }

    /** Compte créé par la scolarité, avec son mot de passe temporaire (affiché une seule fois). */
    public record CompteCree(Utilisateur utilisateur, String motDePasseTemporaire) { }

    public static final int IMPORT_MAX_LIGNES = Tableur.LIGNES_MAX;

    /**
     * Crée un compte de n'importe quel type : étudiant (n° de carte et niveau obligatoires), enseignant
     * ou personnel administratif. Mot de passe temporaire à changer à la première connexion.
     */
    public CompteCree creerCompte(Role role, String prenom, String nom, String email, String telephone,
            String matricule, Niveau niveau) throws SQLException {
        Utilisateur admin = exigerAdmin();
        Objects.requireNonNull(role, "Type de compte absent.");
        Utilisateur u = preparer(role, prenom, nom, email, telephone, matricule, niveau);
        if (utilisateurDAO.existeEmail(u.getEmail())) {
            throw new IllegalArgumentException("Cette adresse e-mail est déjà utilisée.");
        }
        if (u.getMatricule() != null && utilisateurDAO.existeMatricule(u.getMatricule())) {
            throw new IllegalArgumentException("Ce numéro de carte étudiant est déjà utilisé.");
        }
        String temporaire = PasswordUtil.genererMotDePasseTemporaire();
        poserMotDePasse(u, temporaire);
        utilisateurDAO.insert(u);
        journalService.log(admin.getId(), "CREATION_COMPTE", role.getLibelle() + " #" + u.getId() + " " + u.getEmail());
        return new CompteCree(u, temporaire);
    }

    /** Lit un fichier Excel (.xlsx) ou CSV et importe les comptes du type choisi. */
    public List<LigneImport> importerFichier(Role role, Niveau niveauParDefaut, java.nio.file.Path fichier)
            throws SQLException, java.io.IOException {
        exigerAdmin();
        return importerComptes(role, niveauParDefaut, Tableur.lire(fichier));
    }

    /** Ancien format CSV des étudiants (matricule;nom;prénom;email;niveau), conservé. */
    public List<LigneImport> importerEtudiants(java.nio.file.Path csv) throws SQLException, java.io.IOException {
        return importerFichier(Role.ETUDIANT, null, csv);
    }

    /** Ancien format : lignes « matricule;nom;prénom;email;niveau », en-tête facultatif. */
    public List<LigneImport> importerEtudiants(List<String> lignes) throws SQLException {
        exigerAdmin();
        List<Map<String, String>> enregistrements = new java.util.ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            String brute = lignes.get(i).replace("\uFEFF", "").trim();
            if (brute.isEmpty() || (i == 0 && brute.toLowerCase(Locale.ROOT).startsWith("matricule"))) {
                continue;
            }
            String[] c = brute.split(brute.contains(";") ? ";" : ",", -1);
            Map<String, String> m = new java.util.LinkedHashMap<>();
            m.put(Tableur.LIGNE, String.valueOf(i + 1));
            if (c.length != 5) {
                m.put("_erreur", "5 colonnes attendues : matricule;nom;prénom;email;niveau.");
                m.put(Tableur.EMAIL, c.length > 3 ? c[3].trim() : brute);
            } else {
                m.put(Tableur.MATRICULE, c[0].trim());
                m.put(Tableur.NOM, c[1].trim());
                m.put(Tableur.PRENOM, c[2].trim());
                m.put(Tableur.EMAIL, c[3].trim());
                m.put(Tableur.NIVEAU, c[4].trim());
            }
            enregistrements.add(m);
        }
        return importerComptes(Role.ETUDIANT, null, enregistrements);
    }

    /**
     * Inscription en masse par la scolarité. Chaque ligne est traitée indépendamment : une ligne invalide
     * n'empêche pas les autres. Nouveau compte : actif, mot de passe temporaire. Compte existant du même type
     * (même e-mail, et même n° de carte pour un étudiant) : mis à jour (niveau, téléphone, nom).
     * Un compte d'un autre type n'est jamais écrasé.
     */
    public List<LigneImport> importerComptes(Role role, Niveau niveauParDefaut, List<Map<String, String>> lignes)
            throws SQLException {
        Utilisateur admin = exigerAdmin();
        Objects.requireNonNull(role, "Type de compte absent.");
        if (lignes.size() > IMPORT_MAX_LIGNES) {
            throw new IllegalArgumentException("Import limité à " + IMPORT_MAX_LIGNES + " comptes par fichier.");
        }
        List<LigneImport> resultats = new java.util.ArrayList<>();
        java.util.Set<String> vus = new java.util.HashSet<>();
        int crees = 0;
        int majs = 0;
        for (Map<String, String> l : lignes) {
            int numero = Integer.parseInt(l.getOrDefault(Tableur.LIGNE, "0"));
            String id = l.getOrDefault(Tableur.EMAIL, "");
            try {
                if (l.containsKey("_erreur")) {
                    throw new IllegalArgumentException(l.get("_erreur"));
                }
                Niveau niveau = niveauParDefaut;
                String n = l.get(Tableur.NIVEAU);
                if (role == Role.ETUDIANT && n != null && !n.isBlank()) {
                    try {
                        niveau = Niveau.valueOf(n.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Niveau inconnu « " + n.trim() + " » (L1, L2, L3, M1 ou M2).");
                    }
                }
                Utilisateur u = preparer(role, l.get(Tableur.PRENOM), l.get(Tableur.NOM), l.get(Tableur.EMAIL),
                        l.get(Tableur.TELEPHONE), l.get(Tableur.MATRICULE), niveau);
                id = u.getEmail();
                if (!vus.add(u.getEmail()) || (u.getMatricule() != null && !vus.add("#" + u.getMatricule()))) {
                    throw new IllegalArgumentException("Doublon dans le fichier.");
                }

                Utilisateur parEmail = utilisateurDAO.existeEmail(u.getEmail())
                        ? utilisateurDAO.findByEmailOuMatricule(u.getEmail()) : null;
                boolean matriculePris = u.getMatricule() != null && utilisateurDAO.existeMatricule(u.getMatricule());
                if (parEmail != null || matriculePris) {
                    if (parEmail == null || parEmail.getRole() != role
                            || (role == Role.ETUDIANT && !u.getMatricule().equals(parEmail.getMatricule()))) {
                        throw new IllegalArgumentException("E-mail ou n° de carte déjà utilisé par un autre compte.");
                    }
                    parEmail.setPrenom(u.getPrenom());
                    parEmail.setNom(u.getNom());
                    if (u.getTelephone() != null) {
                        parEmail.setTelephone(u.getTelephone());
                    }
                    if (role == Role.ETUDIANT) {
                        parEmail.setNiveau(u.getNiveau());
                    }
                    utilisateurDAO.update(parEmail);
                    majs++;
                    resultats.add(new LigneImport(numero, u.getEmail(), role == Role.ETUDIANT ? "REINSCRIT" : "MIS_A_JOUR",
                            role == Role.ETUDIANT ? "Niveau " + u.getNiveau() : "Coordonnées mises à jour"));
                    continue;
                }

                String temporaire = PasswordUtil.genererMotDePasseTemporaire();
                poserMotDePasse(u, temporaire);
                utilisateurDAO.insert(u);
                crees++;
                resultats.add(new LigneImport(numero, u.getEmail(), "CREE", temporaire));
            } catch (IllegalArgumentException e) {
                resultats.add(new LigneImport(numero, id, "ERREUR", e.getMessage()));
            }
        }
        journalService.log(admin.getId(), role == Role.ETUDIANT ? "IMPORT_ETUDIANTS" : "IMPORT_COMPTES",
                role.getLibelle() + " : " + crees + " compte(s) créé(s), " + majs + " mis à jour, "
                        + (resultats.size() - crees - majs) + " ligne(s) en erreur");
        return resultats;
    }

    /** Modèle Excel à remplir pour l'import d'un type de compte. */
    public static void ecrireModele(Role role, java.nio.file.Path fichier) throws java.io.IOException {
        if (role == Role.ETUDIANT) {
            Tableur.ecrireXlsx(fichier, List.of("Prénom", "Nom", "N° carte étudiant", "Téléphone", "Email", "Niveau"),
                    List.of(List.of("Awa", "SOW", "MCS-2026-010", "+221 77 123 45 67", "awa.sow@ucad.edu.sn", "M2")));
        } else {
            Tableur.ecrireXlsx(fichier, List.of("Prénom", "Nom", "Téléphone", "Email"),
                    List.of(List.of("Mamadou", "DIALLO", "+221 76 987 65 43",
                            role == Role.ADMIN ? "scolarite.mcs@ucad.edu.sn" : "mamadou.diallo@ucad.edu.sn")));
        }
    }

    /** Vérifie et normalise les champs communs à la création et à l'import. */
    private Utilisateur preparer(Role role, String prenom, String nom, String email, String telephone,
            String matricule, Niveau niveau) {
        String p = prenom == null ? "" : prenom.trim();
        String n = nom == null ? "" : nom.trim();
        String e = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String t = telephone == null || telephone.isBlank() ? null : telephone.trim();
        if (!Validator.longueurEntre(p, 1, 80) || !Validator.longueurEntre(n, 1, 80)) {
            throw new IllegalArgumentException("Prénom et nom obligatoires (80 caractères maximum).");
        }
        if (!Validator.emailValide(e)) {
            throw new IllegalArgumentException("Adresse e-mail invalide.");
        }
        if (t != null && !Validator.telephoneValide(t)) {
            throw new IllegalArgumentException("Numéro de téléphone invalide (ex. +221 77 123 45 67).");
        }
        Utilisateur u = new Utilisateur();
        if (role == Role.ETUDIANT) {
            String m = matricule == null ? "" : matricule.trim().toUpperCase(Locale.ROOT);
            if (!Validator.matriculeValide(m)) {
                throw new IllegalArgumentException("Numéro de carte étudiant invalide (4 à 20 lettres, chiffres ou tirets).");
            }
            if (niveau == null) {
                throw new IllegalArgumentException("Niveau obligatoire pour un étudiant (L1 à M2).");
            }
            u.setMatricule(m);
            u.setNiveau(niveau);
        }
        u.setPrenom(p);
        u.setNom(n);
        u.setEmail(e);
        u.setTelephone(t);
        u.setRole(role);
        u.setActif(true);
        u.setDoitChangerMotDePasse(true);
        u.setDateCreation(LocalDate.now());
        return u;
    }

    private static void poserMotDePasse(Utilisateur u, String motDePasse) {
        String sel = PasswordUtil.genererSel();
        u.setSel(sel);
        u.setMotDePasseHash(PasswordUtil.hasher(motDePasse, sel));
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
