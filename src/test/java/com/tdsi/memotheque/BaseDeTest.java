package com.tdsi.memotheque;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.tdsi.memotheque.dao.impl.DomaineDAOImpl;
import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.FichierService;
import com.tdsi.memotheque.service.JournalService;
import com.tdsi.memotheque.service.MemoireService;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.GestionCles;
import com.tdsi.memotheque.util.PasswordUtil;
import com.tdsi.memotheque.util.SessionManager;

/**
 * Outils communs aux tests : base SQLite TEMPORAIRE (jamais memotdsi.db),
 * création de comptes, de domaines et de PDF de test.
 */
final class BaseDeTest {

    static final String MOT_DE_PASSE = "MotDePasse123";

    private BaseDeTest() { }

    /** Crée une base vide dans le dossier temporaire du test et y exécute schema.sql + seed.sql. */
    static void ouvrirBaseTemporaire(Path dossier) throws Exception {
        DatabaseConnection.utiliserBasePourTests(
                "jdbc:sqlite:" + dossier.resolve("test.db").toAbsolutePath());
        GestionCles.utiliserPourTests(dossier);
        FichierService.utiliserDossierParDefaut(dossier.resolve("storage"));
        DatabaseConnection.initialiser();
        SessionManager.fermer();
    }

    /** À appeler dans @AfterEach : aucune trace laissée pour le test suivant. */
    static void fermer() {
        SessionManager.fermer();
        DatabaseConnection.utiliserBaseParDefaut();
        GestionCles.utiliserParDefaut();
        FichierService.utiliserDossierParDefaut(com.tdsi.memotheque.util.Donnees.chemin("storage"));
    }

    static Utilisateur creerUtilisateur(String email, String matricule, Role role,
            Niveau niveau, boolean actif) throws Exception {
        String sel = PasswordUtil.genererSel();

        Utilisateur u = new Utilisateur();
        u.setMatricule(matricule);
        u.setNom("Nom" + role.name());
        u.setPrenom("Prenom");
        u.setEmail(email);
        u.setSel(sel);
        u.setMotDePasseHash(PasswordUtil.hasher(MOT_DE_PASSE, sel));
        u.setRole(role);
        u.setNiveau(niveau);
        u.setActif(actif);
        u.setDateCreation(LocalDate.now());

        new UtilisateurDAOImpl().insert(u);
        return u;
    }

    static Domaine premierDomaine() throws Exception {
        return new DomaineDAOImpl().findAll().get(0);
    }

    /** Un petit PDF valide (commence par %PDF). */
    static Path creerPdf(Path dossier, String nom) throws Exception {
        Path pdf = dossier.resolve(nom);
        // Contenu unique : deux PDF de test n'ont jamais la même empreinte (refus des doublons).
        Files.writeString(pdf, "%PDF-1.4\n% fichier de test " + nom + " " + System.nanoTime()
                + "\n1 0 obj << >> endobj\n%%EOF\n", StandardCharsets.UTF_8);
        return pdf;
    }

    /** Dépose un vrai mémoire (PDF stocké dans dossier/storage) au nom de l'étudiant, puis ferme la session. */
    static Memoire deposer(Path dossier, Utilisateur etudiant, Utilisateur encadreur,
            String annee, Niveau niveau) throws Exception {
        MemoireService service = new MemoireService(new MemoireDAOImpl(), new DomaineDAOImpl(),
                new UtilisateurDAOImpl(), new FichierService(dossier.resolve("storage")),
                new JournalService());
        preparerCampagne(etudiant, annee, niveau);
        SessionManager.ouvrir(etudiant);
        try {
            return service.deposer(memoireValide(premierDomaine(), encadreur, annee, niveau),
                    creerPdf(dossier, "depot-" + System.nanoTime() + ".pdf"));
        } finally {
            SessionManager.fermer();
        }
    }

    /**
     * L'année vient de la scolarité et le niveau de l'inscription de l'étudiant :
     * on positionne les deux pour que le dépôt corresponde au scénario du test.
     */
    static void preparerCampagne(Utilisateur etudiant, String annee, Niveau niveau) throws Exception {
        new com.tdsi.memotheque.dao.impl.ParametreDAOImpl().ecrire("annee_courante", annee);
        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();
        Utilisateur u = dao.findById(etudiant.getId());
        u.setNiveau(niveau);
        dao.update(u);
        etudiant.setNiveau(niveau);
    }

    /**
     * Fait parcourir tout le circuit à un mémoire déposé : avis favorable de l'encadreur,
     * soutenance planifiée par l'admin (président + examinateur créés au besoin), soutenance
     * ramenée dans le passé, PV avec la note donnée. Retourne le mémoire relu en base.
     */
    static Memoire publier(Memoire m, Utilisateur encadreur, Utilisateur admin, double note) throws Exception {
        SessionManager.ouvrir(encadreur);
        new com.tdsi.memotheque.service.ValidationService().donnerAvisFavorable(m.getId());

        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();
        Utilisateur president = dao.findByEmailOuMatricule("president.jury@test.sn");
        if (president == null) {
            president = creerUtilisateur("president.jury@test.sn", null, Role.ENCADREUR, null, true);
        }
        Utilisateur examinateur = dao.findByEmailOuMatricule("examinateur.jury@test.sn");
        if (examinateur == null) {
            examinateur = creerUtilisateur("examinateur.jury@test.sn", null, Role.ENCADREUR, null, true);
        }

        SessionManager.ouvrir(admin);
        com.tdsi.memotheque.service.SoutenanceService soutenances = new com.tdsi.memotheque.service.SoutenanceService();
        soutenances.planifier(m.getId(), java.time.LocalDateTime.now().plusDays(1).withHour(10).withMinute(0)
                .plusMinutes(m.getId() * 90L % 600), "Salle " + m.getId(), president.getId(),
                java.util.List.of(examinateur.getId()));
        soutenanceDansLePasse(m.getId());
        Memoire publie = soutenances.enregistrerProcesVerbal(m.getId(), note, "RAS", null, null, MOT_DE_PASSE);
        SessionManager.fermer();
        return publie;
    }

    /** La soutenance « a eu lieu » : on la ramène une heure en arrière pour pouvoir saisir le PV. */
    static void soutenanceDansLePasse(int idMemoire) throws Exception {
        try (var c = DatabaseConnection.getConnection();
             var ps = c.prepareStatement("UPDATE soutenance SET date_heure = ? WHERE id_memoire = ?")) {
            ps.setString(1, java.time.LocalDateTime.now().minusHours(1).withSecond(0).withNano(0).toString());
            ps.setInt(2, idMemoire);
            ps.executeUpdate();
        }
    }

    /** Mémoire dont tous les champs du formulaire sont valides. */
    static Memoire memoireValide(Domaine domaine, Utilisateur encadreur, String annee, Niveau niveau) {
        Memoire m = new Memoire();
        m.setTitre("Chiffrement homomorphe appliqué au cloud");
        m.setResume("Étude des schémas de chiffrement homomorphe et de leur coût en pratique.");
        m.setMotsCles("cryptographie, cloud, homomorphe");
        m.setAnneeAcademique(annee);
        m.setNiveau(niveau);
        m.setDomaine(domaine);
        m.setEncadreur(encadreur);
        return m;
    }
}
