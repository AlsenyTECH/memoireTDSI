package com.tdsi.memotheque;

import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class UtilisateurDAOImplTest {

    // Base SQLite temporaire : le test ne touche jamais memotdsi.db.
    @TempDir
    java.nio.file.Path dossierTemporaire;

    @AfterEach
    void restaurerBaseParDefaut() {
        DatabaseConnection.utiliserBaseParDefaut();
    }

    @BeforeEach
    void initialiserBase() throws Exception {
        DatabaseConnection.utiliserBasePourTests(
                "jdbc:sqlite:" + dossierTemporaire.resolve("test.db").toAbsolutePath());
        DatabaseConnection.initialiser();

        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();

        Utilisateur existant = dao.findByEmailOuMatricule("test001@memotdsi.test");

        if (existant != null) {
            dao.delete(existant.getId());
        }
    }

    @Test
    void insertPuisFindById() throws Exception {
        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();

        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher("Test1234", sel);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule("TEST001");
        utilisateur.setNom("Test");
        utilisateur.setPrenom("Utilisateur");
        utilisateur.setEmail("test001@memotdsi.test");
        utilisateur.setMotDePasseHash(hash);
        utilisateur.setSel(sel);
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setNiveau(Niveau.M2);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        dao.insert(utilisateur);

        System.out.println("ID après insertion : " + utilisateur.getId());

        Utilisateur trouve = dao.findById(utilisateur.getId());

        assertNotNull(trouve);
        assertEquals("TEST001", trouve.getMatricule());
        assertEquals("test001@memotdsi.test", trouve.getEmail());
        assertEquals(Role.ETUDIANT, trouve.getRole());
        assertEquals(Niveau.M2, trouve.getNiveau());

        dao.delete(utilisateur.getId());
    }

    @Test
    void findByEmailOuMatricule() throws Exception {
        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();

        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher("Test1234", sel);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule("TEST002");
        utilisateur.setNom("Test");
        utilisateur.setPrenom("Recherche");
        utilisateur.setEmail("test002@memotdsi.test");
        utilisateur.setMotDePasseHash(hash);
        utilisateur.setSel(sel);
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setNiveau(Niveau.M2);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        dao.insert(utilisateur);

        // Recherche par email
        Utilisateur trouveParEmail = dao.findByEmailOuMatricule("test002@memotdsi.test");

        assertNotNull(trouveParEmail);
        assertEquals(utilisateur.getId(), trouveParEmail.getId());

        // Recherche par matricule
        Utilisateur trouveParMatricule = dao.findByEmailOuMatricule("TEST002");

        assertNotNull(trouveParMatricule);
        assertEquals(utilisateur.getId(), trouveParMatricule.getId());

        dao.delete(utilisateur.getId());
    }

    @Test
    void existeEmailEtMatricule() throws Exception {
        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();

        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher("Test1234", sel);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule("TEST003");
        utilisateur.setNom("Test");
        utilisateur.setPrenom("Existence");
        utilisateur.setEmail("test003@memotdsi.test");
        utilisateur.setMotDePasseHash(hash);
        utilisateur.setSel(sel);
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setNiveau(Niveau.M2);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        dao.insert(utilisateur);

        // Les deux doivent exister
        assertTrue(dao.existeEmail("test003@memotdsi.test"));
        assertTrue(dao.existeMatricule("TEST003"));

        // Ces deux valeurs ne doivent pas exister
        assertFalse(dao.existeEmail("inconnu@memotdsi.test"));
        assertFalse(dao.existeMatricule("INCONNU"));

        dao.delete(utilisateur.getId());
    }

    @Test
    void findByRole() throws Exception {
        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();

        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher("Test1234", sel);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule("TEST004");
        utilisateur.setNom("Test");
        utilisateur.setPrenom("Role");
        utilisateur.setEmail("test004@memotdsi.test");
        utilisateur.setMotDePasseHash(hash);
        utilisateur.setSel(sel);
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setNiveau(Niveau.M2);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        dao.insert(utilisateur);

        // Recherche des étudiants
        var etudiants = dao.findByRole(Role.ETUDIANT);

        assertFalse(etudiants.isEmpty());

        boolean utilisateurTrouve = etudiants.stream()
                .anyMatch(u -> u.getId() == utilisateur.getId());

        assertTrue(utilisateurTrouve);

        // Un utilisateur ayant le rôle ETUDIANT ne doit pas apparaître
        // dans la liste des ADMIN.
        var administrateurs = dao.findByRole(Role.ADMIN);

        boolean trouveCommeAdmin = administrateurs.stream()
                .anyMatch(u -> u.getId() == utilisateur.getId());

        assertFalse(trouveCommeAdmin);

        dao.delete(utilisateur.getId());
    }

    @Test
    void changerActivation() throws Exception {
        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();

        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher("Test1234", sel);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule("TEST005");
        utilisateur.setNom("Test");
        utilisateur.setPrenom("Activation");
        utilisateur.setEmail("test005@memotdsi.test");
        utilisateur.setMotDePasseHash(hash);
        utilisateur.setSel(sel);
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setNiveau(Niveau.M2);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        dao.insert(utilisateur);

        // Désactiver le compte
        dao.changerActivation(utilisateur.getId(), false);

        Utilisateur trouve = dao.findById(utilisateur.getId());

        assertNotNull(trouve);
        assertFalse(trouve.isActif());

        // Réactiver le compte
        dao.changerActivation(utilisateur.getId(), true);

        trouve = dao.findById(utilisateur.getId());

        assertTrue(trouve.isActif());

        dao.delete(utilisateur.getId());
    }

    @Test
    void mettreAJourTentatives() throws Exception {
        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();

        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher("Test1234", sel);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule("TEST006");
        utilisateur.setNom("Test");
        utilisateur.setPrenom("Tentatives");
        utilisateur.setEmail("test006@memotdsi.test");
        utilisateur.setMotDePasseHash(hash);
        utilisateur.setSel(sel);
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setNiveau(Niveau.M2);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        dao.insert(utilisateur);

        // Enregistrer 3 tentatives échouées et verrouiller le compte
        dao.mettreAJourTentatives(utilisateur.getId(), 3, true);

        Utilisateur trouve = dao.findById(utilisateur.getId());

        assertNotNull(trouve);
        assertEquals(3, trouve.getTentativesEchec());
        assertTrue(trouve.isVerrouille());

        // Réinitialiser les tentatives et déverrouiller le compte
        dao.mettreAJourTentatives(utilisateur.getId(), 0, false);

        trouve = dao.findById(utilisateur.getId());

        assertEquals(0, trouve.getTentativesEchec());
        assertFalse(trouve.isVerrouille());

        dao.delete(utilisateur.getId());
    }

    @Test
    void changerMotDePasse() throws Exception {
        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();

        String ancienSel = PasswordUtil.genererSel();
        String ancienHash = PasswordUtil.hasher("Ancien123", ancienSel);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule("TEST007");
        utilisateur.setNom("Test");
        utilisateur.setPrenom("MotDePasse");
        utilisateur.setEmail("test007@memotdsi.test");
        utilisateur.setMotDePasseHash(ancienHash);
        utilisateur.setSel(ancienSel);
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setNiveau(Niveau.M2);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        dao.insert(utilisateur);

        String nouveauSel = PasswordUtil.genererSel();
        String nouveauHash = PasswordUtil.hasher("Nouveau123", nouveauSel);

        dao.changerMotDePasse(
                utilisateur.getId(),
                nouveauHash,
                nouveauSel);

        Utilisateur trouve = dao.findById(utilisateur.getId());

        assertNotNull(trouve);
        assertEquals(nouveauHash, trouve.getMotDePasseHash());
        assertEquals(nouveauSel, trouve.getSel());

        assertTrue(PasswordUtil.verifier(
                "Nouveau123",
                trouve.getSel(),
                trouve.getMotDePasseHash()));

        assertFalse(PasswordUtil.verifier(
                "Ancien123",
                trouve.getSel(),
                trouve.getMotDePasseHash()));

        dao.delete(utilisateur.getId());
    }

    @Test
    void updateUtilisateur() throws Exception {
        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();

        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher("Test1234", sel);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule("TEST008");
        utilisateur.setNom("Test");
        utilisateur.setPrenom("Avant");
        utilisateur.setEmail("test008@memotdsi.test");
        utilisateur.setMotDePasseHash(hash);
        utilisateur.setSel(sel);
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setNiveau(Niveau.M2);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        dao.insert(utilisateur);

        // Modifier les informations de l'utilisateur
        utilisateur.setNom("NouveauNom");
        utilisateur.setPrenom("Apres");

        dao.update(utilisateur);

        // Relire les données enregistrées
        Utilisateur trouve = dao.findById(utilisateur.getId());

        assertNotNull(trouve);
        assertEquals("NouveauNom", trouve.getNom());
        assertEquals("Apres", trouve.getPrenom());

        dao.delete(utilisateur.getId());
    }

    @Test
    void findAll() throws Exception {
        UtilisateurDAOImpl dao = new UtilisateurDAOImpl();

        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher("Test1234", sel);

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setMatricule("TEST009");
        utilisateur.setNom("Test");
        utilisateur.setPrenom("Liste");
        utilisateur.setEmail("test009@memotdsi.test");
        utilisateur.setMotDePasseHash(hash);
        utilisateur.setSel(sel);
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setNiveau(Niveau.M2);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);
        utilisateur.setDateCreation(LocalDate.now());

        dao.insert(utilisateur);

        var utilisateurs = dao.findAll();

        assertNotNull(utilisateurs);
        assertFalse(utilisateurs.isEmpty());

        boolean utilisateurTrouve = utilisateurs.stream()
                .anyMatch(u -> u.getId() == utilisateur.getId());

        assertTrue(utilisateurTrouve);

        dao.delete(utilisateur.getId());
    }
}