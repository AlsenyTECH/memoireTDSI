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
        DatabaseConnection.initialiser();
        SessionManager.fermer();
    }

    /** À appeler dans @AfterEach : aucune trace laissée pour le test suivant. */
    static void fermer() {
        SessionManager.fermer();
        DatabaseConnection.utiliserBaseParDefaut();
        GestionCles.utiliserParDefaut();
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
        Files.writeString(pdf, "%PDF-1.4\n% fichier de test\n1 0 obj << >> endobj\n%%EOF\n",
                StandardCharsets.US_ASCII);
        return pdf;
    }

    /** Dépose un vrai mémoire (PDF stocké dans dossier/storage) au nom de l'étudiant, puis ferme la session. */
    static Memoire deposer(Path dossier, Utilisateur etudiant, Utilisateur encadreur,
            String annee, Niveau niveau) throws Exception {
        MemoireService service = new MemoireService(new MemoireDAOImpl(), new DomaineDAOImpl(),
                new UtilisateurDAOImpl(), new FichierService(dossier.resolve("storage")),
                new JournalService());
        SessionManager.ouvrir(etudiant);
        try {
            return service.deposer(memoireValide(premierDomaine(), encadreur, annee, niveau),
                    creerPdf(dossier, "depot-" + System.nanoTime() + ".pdf"));
        } finally {
            SessionManager.fermer();
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
