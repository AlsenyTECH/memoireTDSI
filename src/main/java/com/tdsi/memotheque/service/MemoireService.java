package com.tdsi.memotheque.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.tdsi.memotheque.dao.DomaineDAO;
import com.tdsi.memotheque.dao.MemoireDAO;
import com.tdsi.memotheque.dao.MemoireVersionDAO;
import com.tdsi.memotheque.dao.impl.MemoireVersionDAOImpl;
import com.tdsi.memotheque.model.MemoireVersion;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.dao.impl.DomaineDAOImpl;
import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Validator;

/**
 * [M3] Règles de dépôt, modification et suppression d'un mémoire.
 * À faire : voir les règles de validation du dépôt (titre, résumé, mots-clés, année, domaine, encadreur),
 * un seul mémoire par niveau et par année, modification/suppression seulement si EN_ATTENTE ou REFUSE,
 * suppression du fichier physique, remise à EN_ATTENTE après correction d'un refus.
 *
 * Les droits sont vérifiés ici (SessionManager), pas seulement dans l'interface.
 */
public class MemoireService {

    public static final int TITRE_MIN = 5;
    public static final int TITRE_MAX = 200;
    public static final int RESUME_MIN = 20;
    public static final int RESUME_MAX = 5000;
    public static final int MOTS_CLES_MIN = 3;
    public static final int MOTS_CLES_MAX = 10;
    public static final int CO_AUTEUR_MAX = 100;

    private final MemoireDAO memoireDAO;
    private final DomaineDAO domaineDAO;
    private final UtilisateurDAO utilisateurDAO;
    private final FichierService fichierService;
    private final JournalService journalService;
    private final MemoireVersionDAO versionDAO;
    private final NotificationService notifications = new NotificationService();

    private static final Logger LOG = Logger.getLogger(MemoireService.class.getName());

    public MemoireService() {
        this(new MemoireDAOImpl(), new DomaineDAOImpl(), new UtilisateurDAOImpl(),
                new FichierService(), new JournalService());
    }

    // Constructeur utile pour les tests.
    public MemoireService(MemoireDAO memoireDAO, DomaineDAO domaineDAO,
            UtilisateurDAO utilisateurDAO, FichierService fichierService,
            JournalService journalService) {
        this.memoireDAO = Objects.requireNonNull(memoireDAO);
        this.domaineDAO = Objects.requireNonNull(domaineDAO);
        this.utilisateurDAO = Objects.requireNonNull(utilisateurDAO);
        this.fichierService = Objects.requireNonNull(fichierService);
        this.journalService = Objects.requireNonNull(journalService);
        this.versionDAO = new MemoireVersionDAOImpl();
    }

    // ---------------------------------------------------------------- listes du formulaire

    public List<Domaine> listerDomaines() throws SQLException {
        return domaineDAO.findAll();
    }

    /** Encadreurs actifs (liste déroulante du dépôt). */
    public List<Utilisateur> listerEncadreurs() throws SQLException {
        List<Utilisateur> actifs = new ArrayList<>();
        for (Utilisateur u : utilisateurDAO.findByRole(Role.ENCADREUR)) {
            if (u.isActif()) {
                actifs.add(u);
            }
        }
        return actifs;
    }

    /** Mémoires de l'utilisateur connecté (écran « Mes mémoires »). */
    public List<Memoire> mesMemoires() throws SQLException {
        Utilisateur u = exigerConnexion();
        return memoireDAO.findByAuteur(u.getId());
    }

    // ---------------------------------------------------------------- dépôt

    /**
     * Dépose un nouveau mémoire pour l'étudiant connecté.
     * L'auteur, le statut et les dates sont imposés par le service (jamais par le formulaire).
     */
    public Memoire deposer(Memoire m, Path pdf) throws SQLException, IOException {
        Utilisateur etudiant = exigerRole(Role.ETUDIANT);
        Objects.requireNonNull(m, "Mémoire absent.");

        normaliser(m);
        validerChamps(m);

        if (memoireDAO.existePourAuteurNiveauAnnee(
                etudiant.getId(), m.getNiveau(), m.getAnneeAcademique())) {
            throw new IllegalArgumentException("Vous avez déjà déposé un mémoire de niveau "
                    + m.getNiveau() + " pour l'année " + m.getAnneeAcademique() + ".");
        }

        m.setAuteur(etudiant);
        m.setStatut(StatutMemoire.EN_ATTENTE);
        m.setDateDepot(LocalDate.now());
        m.setDatePublication(null);
        m.setMotifRefus(null);
        m.setValidateur(null);
        m.setNbVues(0);
        m.setNbTelechargements(0);

        FichierService.FichierPrepare fichier = fichierService.lire(pdf);
        refuserDoublon(fichier, 0);
        fichierService.stocker(fichier, m);

        try {
            memoireDAO.insert(m);
        } catch (SQLException e) {
            fichierService.supprimer(m.getCheminFichier());
            throw e;
        }

        journalService.log(etudiant.getId(), "DEPOT",
                "Mémoire #" + m.getId() + " « " + m.getTitre() + " » (SHA-256 "
                        + m.getEmpreinteSha256() + ")");
        notifications.notifier(m.getEncadreur().getId(), "Nouveau mémoire à valider : « " + m.getTitre()
                + " » (" + etudiant.getNomComplet() + ", " + m.getNiveau() + " " + m.getAnneeAcademique() + ").");
        return m;
    }

    // ---------------------------------------------------------------- modification

    /**
     * Modifie un mémoire EN_ATTENTE ou REFUSE de l'étudiant connecté.
     * nouveauPdf peut être null (on garde le fichier actuel).
     * Après modification, le mémoire repasse EN_ATTENTE (correction d'un refus).
     */
    public void modifier(Memoire saisie, Path nouveauPdf) throws SQLException, IOException {
        Utilisateur etudiant = exigerRole(Role.ETUDIANT);
        Objects.requireNonNull(saisie, "Mémoire absent.");

        Memoire existant = chargerModifiable(saisie.getId(), etudiant);
        Memoire etatAvant = copieFichier(existant);

        normaliser(saisie);
        validerChamps(saisie);

        boolean changementNiveauOuAnnee = saisie.getNiveau() != existant.getNiveau()
                || !saisie.getAnneeAcademique().equals(existant.getAnneeAcademique());

        if (changementNiveauOuAnnee && memoireDAO.existePourAuteurNiveauAnnee(
                etudiant.getId(), saisie.getNiveau(), saisie.getAnneeAcademique())) {
            throw new IllegalArgumentException("Vous avez déjà un mémoire de niveau "
                    + saisie.getNiveau() + " pour l'année " + saisie.getAnneeAcademique() + ".");
        }

        existant.setTitre(saisie.getTitre());
        existant.setResume(saisie.getResume());
        existant.setMotsCles(saisie.getMotsCles());
        existant.setAnneeAcademique(saisie.getAnneeAcademique());
        existant.setNiveau(saisie.getNiveau());
        existant.setDomaine(saisie.getDomaine());
        existant.setCoAuteur(saisie.getCoAuteur());
        existant.setEncadreur(saisie.getEncadreur());
        existant.setDateSoutenance(saisie.getDateSoutenance());
        existant.setMention(saisie.getMention());

        existant.setStatut(StatutMemoire.EN_ATTENTE);
        existant.setMotifRefus(null);
        existant.setValidateur(null);
        existant.setDatePublication(null);
        existant.setSignature(null);
        existant.setIdCleSignature(null);

        boolean fichierRemplace = nouveauPdf != null;

        if (fichierRemplace) {
            FichierService.FichierPrepare fichier = fichierService.lire(nouveauPdf);
            refuserDoublon(fichier, existant.getId());
            fichierService.stocker(fichier, existant);
            existant.setDateDepot(LocalDate.now());
        }

        try {
            memoireDAO.update(existant);
        } catch (SQLException e) {
            if (fichierRemplace) {
                fichierService.supprimer(existant.getCheminFichier());
            }
            throw e;
        }

        // L'ancien PDF n'est pas supprimé : il rejoint l'historique des versions.
        if (fichierRemplace) {
            try {
                versionDAO.archiver(etatAvant);
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Version non archivée pour le mémoire #" + existant.getId(), e);
            }
        }

        journalService.log(etudiant.getId(), "MODIFICATION_MEMOIRE",
                "Mémoire #" + existant.getId() + (fichierRemplace ? " (nouveau PDF)" : ""));
        notifications.notifier(existant.getEncadreur().getId(), "Mémoire corrigé, à valider de nouveau : « "
                + existant.getTitre() + " » (" + etudiant.getNomComplet() + ").");
    }

    // ---------------------------------------------------------------- suppression

    /** Supprime un mémoire EN_ATTENTE ou REFUSE (son auteur ou un admin) et son fichier. */
    public void supprimer(int idMemoire) throws SQLException, IOException {
        Utilisateur u = exigerConnexion();

        Memoire existant = memoireDAO.findById(idMemoire);
        if (existant == null) {
            throw new IllegalArgumentException("Mémoire introuvable.");
        }

        boolean auteur = existant.getAuteur() != null && existant.getAuteur().getId() == u.getId();
        if (!auteur && u.getRole() != Role.ADMIN) {
            throw new SecurityException("Vous ne pouvez supprimer que vos propres mémoires.");
        }
        if (existant.getStatut() == StatutMemoire.VALIDE) {
            throw new IllegalStateException("Un mémoire validé ne peut plus être supprimé.");
        }

        List<MemoireVersion> versions = versionDAO.findByMemoire(idMemoire);
        memoireDAO.delete(idMemoire);   // les lignes de memoire_version suivent (ON DELETE CASCADE)
        fichierService.supprimer(existant.getCheminFichier());
        for (MemoireVersion v : versions) {
            fichierService.supprimer(v.getCheminFichier());
        }

        journalService.log(u.getId(), "SUPPRESSION_MEMOIRE",
                "Mémoire #" + idMemoire + " « " + existant.getTitre() + " »");
    }

    // ---------------------------------------------------------------- historique

    /** Versions précédentes du PDF : visibles par l'auteur, son encadreur et l'admin. */
    public List<MemoireVersion> historique(int idMemoire) throws SQLException {
        Utilisateur u = exigerConnexion();
        Memoire m = memoireDAO.findById(idMemoire);
        if (m == null) {
            throw new IllegalArgumentException("Mémoire introuvable.");
        }
        boolean auteur = m.getAuteur() != null && m.getAuteur().getId() == u.getId();
        boolean encadreur = m.getEncadreur() != null && m.getEncadreur().getId() == u.getId();
        if (!auteur && !encadreur && u.getRole() != Role.ADMIN) {
            throw new SecurityException("Historique réservé à l'auteur, à l'encadreur et à l'administrateur.");
        }
        return versionDAO.findByMemoire(idMemoire);
    }

    // ---------------------------------------------------------------- maintenance

    /**
     * Chiffre les PDF déposés avant l'activation du chiffrement (tâche de démarrage).
     * Chaque fichier est vérifié (SHA-256) avant d'être chiffré, puis l'original en clair est supprimé.
     *
     * @return nombre de fichiers chiffrés
     */
    public int chiffrerAnciensFichiers() throws SQLException {
        int nombre = 0;

        for (Memoire m : memoireDAO.findAll()) {
            if (!FichierService.estEnClair(m.getCheminFichier())) {
                continue;
            }
            try {
                String ancien = m.getCheminFichier();
                byte[] clair = fichierService.lireContenuVerifie(m);
                fichierService.stocker(new FichierService.FichierPrepare(clair, m.getEmpreinteSha256(),
                        m.getNomOriginal()), m);
                memoireDAO.update(m);
                fichierService.supprimer(ancien);
                nombre++;
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "PDF du mémoire #" + m.getId() + " non chiffré : " + e.getMessage());
            }
        }

        for (MemoireVersion v : versionDAO.findAll()) {
            if (!FichierService.estEnClair(v.getCheminFichier())) {
                continue;
            }
            try {
                byte[] clair = fichierService.lireContenuVerifie(v.getCheminFichier(), v.getEmpreinteSha256());
                Memoire temporaire = new Memoire();
                fichierService.stocker(new FichierService.FichierPrepare(clair, v.getEmpreinteSha256(),
                        v.getNomOriginal()), temporaire);
                versionDAO.changerChemin(v.getId(), temporaire.getCheminFichier());
                fichierService.supprimer(v.getCheminFichier());
                nombre++;
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Version #" + v.getId() + " non chiffrée : " + e.getMessage());
            }
        }

        if (nombre > 0) {
            journalService.log(null, "CHIFFREMENT_FICHIERS", nombre + " ancien(s) PDF chiffré(s) en AES-256-GCM");
        }
        return nombre;
    }

    // ---------------------------------------------------------------- règles

    /** Contrôle des champs du formulaire (aussi utilisable par le contrôleur avant envoi). */
    public void validerChamps(Memoire m) throws SQLException {
        if (!Validator.longueurEntre(m.getTitre(), TITRE_MIN, TITRE_MAX)) {
            throw new IllegalArgumentException("Le titre doit contenir entre "
                    + TITRE_MIN + " et " + TITRE_MAX + " caractères.");
        }
        if (!Validator.longueurEntre(m.getResume(), RESUME_MIN, RESUME_MAX)) {
            throw new IllegalArgumentException("Le résumé doit contenir entre "
                    + RESUME_MIN + " et " + RESUME_MAX + " caractères.");
        }

        int nbMots = Validator.compterMotsCles(m.getMotsCles());
        if (nbMots < MOTS_CLES_MIN || nbMots > MOTS_CLES_MAX) {
            throw new IllegalArgumentException("Indiquez entre " + MOTS_CLES_MIN + " et "
                    + MOTS_CLES_MAX + " mots-clés séparés par des virgules.");
        }

        if (!Validator.anneeAcademiqueValide(m.getAnneeAcademique())) {
            throw new IllegalArgumentException(
                    "Année académique invalide (format AAAA-AAAA, ex. 2025-2026).");
        }
        int debut = Integer.parseInt(m.getAnneeAcademique().substring(0, 4));
        if (debut < 2000 || debut > LocalDate.now().getYear()) {
            throw new IllegalArgumentException("Année académique hors période autorisée.");
        }

        if (m.getNiveau() == null) {
            throw new IllegalArgumentException("Le niveau est obligatoire.");
        }

        if (m.getDomaine() == null || domaineDAO.findById(m.getDomaine().getId()) == null) {
            throw new IllegalArgumentException("Choisissez un domaine valide.");
        }

        if (m.getEncadreur() == null) {
            throw new IllegalArgumentException("L'encadreur est obligatoire.");
        }
        Utilisateur encadreur = utilisateurDAO.findById(m.getEncadreur().getId());
        if (encadreur == null || encadreur.getRole() != Role.ENCADREUR || !encadreur.isActif()) {
            throw new IllegalArgumentException("Choisissez un encadreur valide.");
        }

        if (m.getCoAuteur() != null && m.getCoAuteur().length() > CO_AUTEUR_MAX) {
            throw new IllegalArgumentException(
                    "Le co-auteur ne doit pas dépasser " + CO_AUTEUR_MAX + " caractères.");
        }
    }

    private void normaliser(Memoire m) {
        m.setTitre(trimOuNull(m.getTitre()));
        m.setResume(trimOuNull(m.getResume()));
        m.setAnneeAcademique(trimOuNull(m.getAnneeAcademique()));
        m.setCoAuteur(trimOuNull(m.getCoAuteur()));

        // Mots-clés : on retire les espaces et les éléments vides (« a , ,b » -> « a, b »).
        if (m.getMotsCles() != null) {
            List<String> mots = new ArrayList<>();
            for (String mot : m.getMotsCles().split(",")) {
                if (!mot.isBlank()) {
                    mots.add(mot.trim());
                }
            }
            m.setMotsCles(String.join(", ", mots));
        }
    }

    /** Un même PDF (même SHA-256) ne peut pas être déposé deux fois (premier niveau anti-plagiat). */
    private void refuserDoublon(FichierService.FichierPrepare fichier, int idExclu) throws SQLException {
        if (memoireDAO.existeEmpreinte(fichier.sha256(), idExclu)) {
            throw new IllegalArgumentException(
                    "Ce fichier PDF a déjà été déposé sur la plateforme (empreinte SHA-256 identique).");
        }
    }

    /** Photographie du fichier actuel avant remplacement (pour l'historique). */
    private Memoire copieFichier(Memoire m) {
        Memoire c = new Memoire();
        c.setId(m.getId());
        c.setCheminFichier(m.getCheminFichier());
        c.setNomOriginal(m.getNomOriginal());
        c.setTailleOctets(m.getTailleOctets());
        c.setEmpreinteSha256(m.getEmpreinteSha256());
        c.setDateDepot(m.getDateDepot());
        c.setStatut(m.getStatut());
        c.setMotifRefus(m.getMotifRefus());
        return c;
    }

    private String trimOuNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Memoire chargerModifiable(int idMemoire, Utilisateur etudiant) throws SQLException {
        Memoire existant = memoireDAO.findById(idMemoire);

        if (existant == null) {
            throw new IllegalArgumentException("Mémoire introuvable.");
        }
        if (existant.getAuteur() == null || existant.getAuteur().getId() != etudiant.getId()) {
            throw new SecurityException("Vous ne pouvez modifier que vos propres mémoires.");
        }
        if (existant.getStatut() == StatutMemoire.VALIDE) {
            throw new IllegalStateException("Un mémoire validé ne peut plus être modifié.");
        }
        return existant;
    }

    private Utilisateur exigerConnexion() {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null) {
            throw new SecurityException("Vous devez être connecté.");
        }
        return u;
    }

    private Utilisateur exigerRole(Role role) {
        Utilisateur u = exigerConnexion();
        if (!SessionManager.aLeRole(role)) {
            throw new SecurityException("Action réservée au profil " + role.getLibelle() + ".");
        }
        return u;
    }
}
