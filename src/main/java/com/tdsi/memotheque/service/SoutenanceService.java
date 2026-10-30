package com.tdsi.memotheque.service;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.tdsi.memotheque.dao.MemoireDAO;
import com.tdsi.memotheque.dao.SoutenanceDAO;
import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.dao.ValidationDAO;
import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.SoutenanceDAOImpl;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.dao.impl.ValidationDAOImpl;
import com.tdsi.memotheque.model.MembreJury;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Mention;
import com.tdsi.memotheque.model.NiveauDiffusion;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.RoleJury;
import com.tdsi.memotheque.model.Soutenance;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.SessionManager;

/**
 * Soutenances (rôle de la scolarité / administration) :
 *  1. planifier : date, salle, jury (président + examinateur(s) + encadreur d'office),
 *     avec détection des conflits de salle et de membres du jury ;
 *  2. après la soutenance, saisir le procès-verbal : note /20 -> mention calculée,
 *     décision admis (publication signée Ed25519) ou ajourné (retour à l'étudiant).
 *
 * L'étudiant ne fixe ni la date ni la mention : elles viennent d'ici.
 */
public class SoutenanceService {

    public static final int DUREE_MINUTES = Soutenance.DUREE_MINUTES;
    public static final int EMBARGO_MAX_ANNEES = 5;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm");

    private final SoutenanceDAO soutenanceDAO;
    private final MemoireDAO memoireDAO;
    private final UtilisateurDAO utilisateurDAO;
    private final ValidationDAO validationDAO;
    private final FichierService fichierService;
    private final SignatureService signatureService;
    private final JournalService journalService;
    private final NotificationService notifications;

    public SoutenanceService() {
        this(new SoutenanceDAOImpl(), new MemoireDAOImpl(), new UtilisateurDAOImpl(), new ValidationDAOImpl(),
                new FichierService(), new SignatureService(), new JournalService(), new NotificationService());
    }

    public SoutenanceService(SoutenanceDAO soutenanceDAO, MemoireDAO memoireDAO, UtilisateurDAO utilisateurDAO,
            ValidationDAO validationDAO, FichierService fichierService, SignatureService signatureService,
            JournalService journalService, NotificationService notifications) {
        this.soutenanceDAO = Objects.requireNonNull(soutenanceDAO);
        this.memoireDAO = Objects.requireNonNull(memoireDAO);
        this.utilisateurDAO = Objects.requireNonNull(utilisateurDAO);
        this.validationDAO = Objects.requireNonNull(validationDAO);
        this.fichierService = Objects.requireNonNull(fichierService);
        this.signatureService = Objects.requireNonNull(signatureService);
        this.journalService = Objects.requireNonNull(journalService);
        this.notifications = Objects.requireNonNull(notifications);
    }

    // ------------------------------------------------------------------ consultation

    /** Mémoires autorisés à soutenir dont la soutenance n'est pas encore planifiée (administration). */
    public List<Memoire> aPlanifier() throws SQLException {
        exigerAdmin();
        Set<Integer> planifies = new HashSet<>();
        for (Soutenance s : soutenanceDAO.findAll()) {
            if (s.getStatut() == Soutenance.Statut.PLANIFIEE) {
                planifies.add(s.getIdMemoire());
            }
        }
        List<Memoire> resultat = new ArrayList<>();
        for (Memoire m : memoireDAO.findAll()) {
            if (m.getStatut() == StatutMemoire.AUTORISE && !planifies.contains(m.getId())) {
                resultat.add(m);
            }
        }
        return resultat;
    }

    /** Administration : toutes les soutenances. Enseignant : celles où il siège. Étudiant : la sienne. */
    public List<Soutenance> soutenances() throws SQLException {
        Utilisateur u = exigerConnexion();
        return switch (u.getRole()) {
            case ADMIN -> soutenanceDAO.findAll();
            case ENCADREUR -> soutenanceDAO.findByEnseignant(u.getId());
            case ETUDIANT -> soutenanceDAO.findAll().stream()
                    .filter(s -> s.getMemoire().getAuteur() != null && s.getMemoire().getAuteur().getId() == u.getId())
                    .toList();
        };
    }

    /** Soutenance d'un mémoire, visible par l'administration et les personnes impliquées. */
    public Soutenance soutenanceDe(int idMemoire) throws SQLException {
        Utilisateur u = exigerConnexion();
        Soutenance s = soutenanceDAO.findByMemoire(idMemoire);
        if (s == null) {
            return null;
        }
        boolean implique = u.getRole() == Role.ADMIN || s.roleDe(u.getId()) != null
                || (s.getMemoire().getAuteur() != null && s.getMemoire().getAuteur().getId() == u.getId());
        if (!implique) {
            throw new SecurityException("Soutenance non accessible.");
        }
        return s;
    }

    /** Enseignants actifs pouvant siéger dans un jury. */
    public List<Utilisateur> enseignants() throws SQLException {
        return utilisateurDAO.findByRole(Role.ENCADREUR).stream().filter(Utilisateur::isActif).toList();
    }

    // ------------------------------------------------------------------ planification

    /**
     * Planifie (ou replanifie) la soutenance d'un mémoire autorisé à soutenir.
     * Jury : un président, au moins un examinateur, et l'encadreur ajouté d'office.
     */
    public Soutenance planifier(int idMemoire, LocalDateTime dateHeure, String salle, int idPresident,
            List<Integer> examinateurs) throws SQLException {
        Utilisateur admin = exigerAdmin();
        Memoire m = memoireDAO.findById(idMemoire);
        if (m == null) {
            throw new IllegalArgumentException("Mémoire introuvable.");
        }
        if (m.getStatut() != StatutMemoire.AUTORISE) {
            throw new IllegalStateException("Seul un mémoire autorisé à soutenir peut être programmé.");
        }
        // Une soutenance déjà tenue (ajournement puis nouvelle autorisation) est remplacée.
        Soutenance existante = soutenanceDAO.findByMemoire(idMemoire);
        if (existante != null && existante.getStatut() == Soutenance.Statut.TENUE) {
            existante = null;
        }
        if (dateHeure == null || !dateHeure.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("La soutenance doit être programmée dans le futur.");
        }
        if (dateHeure.getHour() < 7 || dateHeure.plusMinutes(DUREE_MINUTES).getHour() > 20) {
            throw new IllegalArgumentException("Horaire hors des heures d'ouverture (7h - 20h).");
        }
        if (salle == null || salle.isBlank() || salle.trim().length() > 50) {
            throw new IllegalArgumentException("Indiquez la salle (50 caractères maximum).");
        }
        if (examinateurs == null || examinateurs.isEmpty()) {
            throw new IllegalArgumentException("Le jury doit compter au moins un examinateur en plus du président.");
        }

        int idEncadreur = m.getEncadreur() == null ? -1 : m.getEncadreur().getId();
        List<MembreJury> jury = new ArrayList<>();
        Set<Integer> dejaPris = new HashSet<>();
        jury.add(new MembreJury(enseignantValide(idPresident, idEncadreur, dejaPris), RoleJury.PRESIDENT));
        for (int id : examinateurs) {
            jury.add(new MembreJury(enseignantValide(id, idEncadreur, dejaPris), RoleJury.EXAMINATEUR));
        }
        if (m.getEncadreur() != null) {
            jury.add(new MembreJury(m.getEncadreur(), RoleJury.ENCADREUR));
        }

        List<Integer> ids = jury.stream().map(j -> j.enseignant().getId()).toList();
        List<Soutenance> conflits = soutenanceDAO.conflits(dateHeure, dateHeure.plusMinutes(DUREE_MINUTES),
                salle, ids, idMemoire);
        if (!conflits.isEmpty()) {
            Soutenance c = conflits.get(0);
            throw new IllegalStateException("Conflit avec la soutenance de « " + c.getTitre() + " » le "
                    + c.getDateHeureLisible() + " en " + c.getSalle()
                    + " (même salle ou membre du jury déjà mobilisé).");
        }

        Soutenance s = new Soutenance();
        s.setMemoire(m);
        s.setDateHeure(dateHeure.withSecond(0).withNano(0));
        s.setSalle(salle.trim());
        s.setIdPlanificateur(admin.getId());
        s.setDatePlanification(LocalDateTime.now());
        s.setJury(jury);
        soutenanceDAO.enregistrer(s);

        String quand = s.getDateHeure().format(FORMAT) + ", salle " + s.getSalle();
        journalService.log(admin.getId(), existante == null ? "SOUTENANCE_PLANIFIEE" : "SOUTENANCE_REPLANIFIEE",
                "Mémoire #" + idMemoire + " le " + quand + " ; jury : " + s.getJuryLisible());
        notifications.notifier(m.getAuteur().getId(), "Votre soutenance de « " + m.getTitre() + " » est "
                + (existante == null ? "programmée" : "reprogrammée") + " le " + quand + ".");
        for (MembreJury j : jury) {
            notifications.notifier(j.enseignant().getId(), "Jury (" + j.role().getLibelle().toLowerCase(Locale.ROOT)
                    + ") : soutenance de « " + m.getTitre() + " » (" + m.getAuteurNomComplet() + ") le " + quand + ".");
        }
        return s;
    }

    /** Annule une soutenance planifiée (le mémoire reste autorisé à soutenir). */
    public void annuler(int idMemoire, String raison) throws SQLException {
        Utilisateur admin = exigerAdmin();
        Soutenance s = soutenanceDAO.findByMemoire(idMemoire);
        if (s == null || s.getStatut() != Soutenance.Statut.PLANIFIEE) {
            throw new IllegalStateException("Aucune soutenance planifiée pour ce mémoire.");
        }
        soutenanceDAO.supprimer(idMemoire);
        String motif = raison == null || raison.isBlank() ? "" : " Motif : " + raison.trim();
        journalService.log(admin.getId(), "SOUTENANCE_ANNULEE", "Mémoire #" + idMemoire + "." + motif);
        notifications.notifier(s.getMemoire().getAuteur().getId(),
                "Votre soutenance du " + s.getDateHeureLisible() + " est annulée." + motif);
        for (MembreJury j : s.getJury()) {
            notifications.notifier(j.enseignant().getId(), "Soutenance annulée : « " + s.getTitre()
                    + " » du " + s.getDateHeureLisible() + "." + motif);
        }
    }

    // ------------------------------------------------------------------ procès-verbal

    /**
     * Saisit le PV d'une soutenance qui a eu lieu.
     * Note >= 10 : admis, mention calculée, mémoire publié et PV signé (Ed25519) par l'administrateur.
     * Note < 10 : ajourné, le mémoire retourne à l'étudiant avec les observations du jury.
     *
     * @param motDePasse mot de passe de l'administrateur (ré-authentification, déverrouille sa clé de signature)
     */
    public Memoire enregistrerProcesVerbal(int idMemoire, double note, String observations,
            NiveauDiffusion diffusion, LocalDate finEmbargo, String motDePasse) throws SQLException, IOException {
        Utilisateur admin = exigerAdmin();
        Soutenance s = soutenanceDAO.findByMemoire(idMemoire);
        if (s == null || s.getStatut() != Soutenance.Statut.PLANIFIEE) {
            throw new IllegalStateException("Aucune soutenance planifiée pour ce mémoire.");
        }
        if (s.getDateHeure().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Le procès-verbal ne peut être saisi qu'après la soutenance ("
                    + s.getDateHeureLisible() + ").");
        }
        if (Double.isNaN(note) || note < 0 || note > 20) {
            throw new IllegalArgumentException("La note doit être comprise entre 0 et 20.");
        }
        if (motDePasse == null || motDePasse.isEmpty()) {
            throw new IllegalArgumentException("Confirmez votre mot de passe pour signer le procès-verbal.");
        }
        Memoire m = memoireDAO.findById(idMemoire);
        if (m.getStatut() != StatutMemoire.AUTORISE) {
            throw new IllegalStateException("Ce mémoire n'est pas en attente de soutenance.");
        }
        String obs = observations == null || observations.isBlank() ? null : observations.trim();
        double noteArrondie = Math.round(note * 100) / 100.0;

        Mention mention = mentionPour(noteArrondie);
        if (mention == null) {
            String motif = "Ajourné en soutenance (" + format(noteArrondie) + "/20)"
                    + (obs == null ? "." : " : " + obs);
            validationDAO.ajourner(idMemoire, admin.getId(), motif, noteArrondie);
            soutenanceDAO.marquerTenue(idMemoire, obs);
            journalService.log(admin.getId(), "PV_AJOURNEMENT", "Mémoire #" + idMemoire + " : " + motif);
            notifications.notifier(m.getAuteur().getId(), "Résultat de votre soutenance de « " + m.getTitre()
                    + " » : " + motif + " Vous pouvez déposer une version corrigée.");
            return memoireDAO.findById(idMemoire);
        }

        NiveauDiffusion d = diffusion == null ? m.getNiveauDiffusion() : diffusion;
        LocalDate embargo = verifierEmbargo(d, d == m.getNiveauDiffusion() && finEmbargo == null
                ? m.getDateFinEmbargo() : finEmbargo);

        // Le PDF publié doit être intact (tag GCM + SHA-256), sinon on ne signe rien.
        fichierService.lireContenuVerifie(m);

        LocalDate aujourdhui = LocalDate.now();
        m.setMention(mention);
        m.setNote(noteArrondie);
        m.setDateSoutenance(s.getDateHeure().toLocalDate());
        m.setNiveauDiffusion(d);
        m.setDateFinEmbargo(embargo);
        m.setValidateur(admin);
        m.setDatePublication(aujourdhui);

        SignatureService.SignatureCalculee signature;
        try {
            signature = signatureService.signer(admin, motDePasse, m, aujourdhui);
        } catch (SecurityException e) {
            journalService.log(admin.getId(), "ECHEC_SIGNATURE", "PV du mémoire #" + idMemoire + " : mot de passe incorrect");
            throw e;
        }

        validationDAO.publier(m, signature.signature(), signature.idCle());
        soutenanceDAO.marquerTenue(idMemoire, obs);

        journalService.log(admin.getId(), "PV_PUBLICATION", "Mémoire #" + idMemoire + " « " + m.getTitre()
                + " » : " + format(noteArrondie) + "/20, mention " + mention.getLibelle() + ", diffusion "
                + d.getLibelle() + ", PV signé (clé " + signature.empreinteCle() + ")");
        notifications.notifier(m.getAuteur().getId(), "Félicitations ! Vous êtes admis(e) avec la mention « "
                + mention.getLibelle() + " » (" + format(noteArrondie) + "/20). Votre mémoire « " + m.getTitre()
                + " » est publié" + (d == NiveauDiffusion.CONFIDENTIEL ? " sous embargo jusqu'au " + embargo : "") + ".");
        if (m.getEncadreur() != null) {
            notifications.notifier(m.getEncadreur().getId(), "PV enregistré pour « " + m.getTitre() + " » : mention "
                    + mention.getLibelle() + ". Le mémoire est publié.");
        }
        return memoireDAO.findById(idMemoire);
    }

    /** Barème LMD : < 10 ajourné ; [10,12[ Passable ; [12,14[ Assez bien ; [14,16[ Bien ; >= 16 Très bien. */
    public static Mention mentionPour(double note) {
        if (note < 10) {
            return null;
        }
        if (note < 12) {
            return Mention.PASSABLE;
        }
        if (note < 14) {
            return Mention.ASSEZ_BIEN;
        }
        return note < 16 ? Mention.BIEN : Mention.TRES_BIEN;
    }

    /** Embargo : obligatoire et dans les 5 ans pour un mémoire confidentiel, absent sinon. */
    public static LocalDate verifierEmbargo(NiveauDiffusion diffusion, LocalDate fin) {
        if (diffusion != NiveauDiffusion.CONFIDENTIEL) {
            return null;
        }
        LocalDate aujourdhui = LocalDate.now();
        if (fin == null || !fin.isAfter(aujourdhui)) {
            throw new IllegalArgumentException("Un mémoire confidentiel exige une date de fin d'embargo future.");
        }
        if (fin.isAfter(aujourdhui.plusYears(EMBARGO_MAX_ANNEES))) {
            throw new IllegalArgumentException("L'embargo ne peut pas dépasser " + EMBARGO_MAX_ANNEES + " ans.");
        }
        return fin;
    }

    // ------------------------------------------------------------------ outils

    private Utilisateur enseignantValide(int id, int idEncadreur, Set<Integer> dejaPris) throws SQLException {
        Utilisateur u = utilisateurDAO.findById(id);
        if (u == null || u.getRole() != Role.ENCADREUR || !u.isActif()) {
            throw new IllegalArgumentException("Membre du jury invalide : il doit s'agir d'un enseignant actif.");
        }
        if (id == idEncadreur) {
            throw new IllegalArgumentException("L'encadreur siège d'office : choisissez d'autres enseignants "
                    + "comme président et examinateurs.");
        }
        if (!dejaPris.add(id)) {
            throw new IllegalArgumentException("Un même enseignant ne peut pas occuper deux places dans le jury.");
        }
        return u;
    }

    private static String format(double note) {
        return String.format(Locale.FRANCE, "%.2f", note);
    }

    private Utilisateur exigerConnexion() {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null) {
            throw new SecurityException("Vous devez être connecté.");
        }
        return u;
    }

    private Utilisateur exigerAdmin() {
        Utilisateur u = exigerConnexion();
        if (!SessionManager.aLeRole(Role.ADMIN)) {
            throw new SecurityException("Action réservée à l'administration (scolarité).");
        }
        return u;
    }
}
