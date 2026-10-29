
package com.tdsi.memotheque;

import com.tdsi.memotheque.model.Niveau;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.util.PasswordUtil;
import com.tdsi.memotheque.util.SessionManager;

class AuthServiceTest {

    private AuthService authService;
    private UtilisateurDAO dao;
    private Utilisateur utilisateur;

    @BeforeEach
    void preparer() {
        utilisateur = new Utilisateur();
        utilisateur.setId(1);
        utilisateur.setEmail("etudiant@tdsi.sn");
        utilisateur.setMatricule("TDSI2026");
        utilisateur.setRole(Role.ETUDIANT);
        utilisateur.setActif(true);
        utilisateur.setTentativesEchec(0);
        utilisateur.setVerrouille(false);

        String sel = PasswordUtil.genererSel();
        utilisateur.setSel(sel);
        utilisateur.setMotDePasseHash(
                PasswordUtil.hasher("MotDePasseCorrect123", sel));

        dao = new UtilisateurDAO() {
            @Override
            public Utilisateur findByEmailOuMatricule(String identifiant) {
                if (identifiant.equalsIgnoreCase(utilisateur.getEmail())
                        || identifiant.equalsIgnoreCase(
                                utilisateur.getMatricule())) {
                    return utilisateur;
                }
                return null;
            }

            @Override
            public void mettreAJourTentatives(
                    int id, int tentatives, boolean verrouille) {
                utilisateur.setTentativesEchec(tentatives);
                utilisateur.setVerrouille(verrouille);
            }

            @Override
            public boolean existeMatricule(String matricule) {
                return utilisateur != null
                        && utilisateur.getMatricule() != null
                        && utilisateur.getMatricule().equalsIgnoreCase(matricule);
            }

            @Override
            public boolean existeEmail(String email) {
                return utilisateur != null
                        && utilisateur.getEmail() != null
                        && utilisateur.getEmail().equalsIgnoreCase(email);
            }

            @Override
            public List<Utilisateur> findByRole(Role role) {
                return List.of();
            }

            @Override
            public void changerActivation(int id, boolean actif) {
                utilisateur.setActif(actif);
            }

            @Override
            public void changerMotDePasse(
                    int id, String hash, String sel) {
                utilisateur.setMotDePasseHash(hash);
                utilisateur.setSel(sel);
            }

            @Override
            public void insert(Utilisateur u) {
                u.setId(2);
                utilisateur = u;
            }

            @Override
            public void update(Utilisateur u) {
                throw new UnsupportedOperationException("Non utilisé ici");
            }

            @Override
            public void delete(int id) {
                throw new UnsupportedOperationException("Non utilisé ici");
            }

            @Override
            public Utilisateur findById(int id) {
                return utilisateur.getId() == id ? utilisateur : null;
            }

            @Override
            public List<Utilisateur> findAll() {
                return List.of(utilisateur);
            }
        };

        authService = new AuthService(dao);
        SessionManager.fermer();
    }

    @Test
    void cinquiemeEchecDoitVerrouillerLeCompte() throws SQLException {
        for (int i = 1; i <= 5; i++) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> authService.connecter(
                            "etudiant@tdsi.sn", "MauvaisMotDePasse"));
        }

        assertEquals(5, utilisateur.getTentativesEchec());
        assertTrue(utilisateur.isVerrouille());
    }

    @Test
    void connexionReussieDoitOuvrirLaSessionEtReinitialiserLeCompteur()
            throws SQLException {
        utilisateur.setTentativesEchec(3);

        Utilisateur connecte = authService.connecter(
                "etudiant@tdsi.sn", "MotDePasseCorrect123");

        assertSame(utilisateur, connecte);
        assertEquals(0, utilisateur.getTentativesEchec());
        assertFalse(utilisateur.isVerrouille());
        assertTrue(SessionManager.estConnecte());
    }

    @Test
    void inscriptionValideDoitCreerUnCompteInactifAvecMotDePasseHache()
            throws SQLException {

        final Utilisateur[] compteInscrit = new Utilisateur[1];

        UtilisateurDAO daoInscription = new UtilisateurDAO() {

            @Override
            public boolean existeMatricule(String matricule) {
                return compteInscrit[0] != null
                        && compteInscrit[0].getMatricule() != null
                        && compteInscrit[0].getMatricule().equalsIgnoreCase(matricule);
            }

            @Override
            public boolean existeEmail(String email) {
                return compteInscrit[0] != null
                        && compteInscrit[0].getEmail() != null
                        && compteInscrit[0].getEmail().equalsIgnoreCase(email);
            }

            @Override
            public void insert(Utilisateur u) {
                u.setId(2);
                compteInscrit[0] = u;
            }

            @Override
            public Utilisateur findByEmailOuMatricule(String identifiant) {
                return null;
            }

            @Override
            public void mettreAJourTentatives(
                    int id, int tentatives, boolean verrouille) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Utilisateur> findByRole(Role role) {
                return List.of();
            }

            @Override
            public void changerActivation(int id, boolean actif) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void changerMotDePasse(int id, String hash, String sel) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void update(Utilisateur u) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(int id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Utilisateur findById(int id) {
                return null;
            }

            @Override
            public List<Utilisateur> findAll() {
                return List.of();
            }
        };

        AuthService serviceInscription = new AuthService(daoInscription);

        serviceInscription.inscrire(
                "TDSI2027",
                "Ba",
                "Ahmadou",
                "ahmadou.test@tdsi.sn",
                "MotDePasseValide123",
                Niveau.M1);

        // À compléter : nous devons pouvoir examiner le compte inséré.
        assertNotNull(compteInscrit[0], "Le compte doit être créé.");

        Utilisateur inscrit = compteInscrit[0];

        assertEquals("TDSI2027", inscrit.getMatricule());
        assertEquals("ahmadou.test@tdsi.sn", inscrit.getEmail());

        assertFalse(inscrit.isActif(),
                "Un nouveau compte doit être inactif.");

        assertNotEquals("MotDePasseValide123",
                inscrit.getMotDePasseHash(),
                "Le mot de passe ne doit pas être stocké en clair.");

        assertTrue(
                PasswordUtil.verifier(
                        "MotDePasseValide123",
                        inscrit.getSel(),
                        inscrit.getMotDePasseHash()),
                "Le hash doit correspondre au mot de passe fourni.");
    }

    @Test
    void inscriptionAvecMatriculeExistantDoitEtreRefusee() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.inscrire(
                        "TDSI2026",
                        "Ba",
                        "Ahmadou",
                        "nouveau@tdsi.sn",
                        "MotDePasseValide123",
                        Niveau.M1));

        assertEquals(
                "Ce matricule est déjà utilisé.",
                exception.getMessage());
    }

    @Test
    void inscriptionAvecEmailExistantDoitEtreRefusee() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.inscrire(
                        "TDSI2027",
                        "Ba",
                        "Ahmadou",
                        "etudiant@tdsi.sn",
                        "MotDePasseValide123",
                        Niveau.M1));

        assertEquals(
                "Cette adresse e-mail est déjà utilisée.",
                exception.getMessage());
    }

    @Test
    void inscriptionAvecMotDePasseTropCourtDoitEtreRefusee() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.inscrire(
                        "TDSI2027",
                        "Ba",
                        "Ahmadou",
                        "nouveau@tdsi.sn",
                        "abc123",
                        Niveau.M1));

        assertEquals(
                "Le mot de passe doit contenir entre 8 et 128 caractères.",
                exception.getMessage());
    }

    @Test
    void compteInactifNeDoitPasPouvoirSeConnecter() {
        utilisateur.setActif(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.connecter(
                        "etudiant@tdsi.sn",
                        "MotDePasseCorrect123"));

        assertEquals(
                "Ce compte n'est pas encore activé.",
                exception.getMessage());

        assertFalse(SessionManager.estConnecte());
    }

    @Test
    void compteVerrouilleNeDoitPasPouvoirSeConnecter() {
        utilisateur.setVerrouille(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.connecter(
                        "etudiant@tdsi.sn",
                        "MotDePasseCorrect123"));

        assertEquals(
                "Ce compte est verrouillé.",
                exception.getMessage());

        assertFalse(SessionManager.estConnecte());
    }

    @Test
    void connexionAvecMatriculeEnMinusculesDoitFonctionner()
            throws SQLException {

        Utilisateur connecte = authService.connecter(
                "tdsi2026",
                "MotDePasseCorrect123");

        assertSame(utilisateur, connecte);
        assertTrue(SessionManager.estConnecte());
    }

    @Test
    void initialisationDemoDoitRefuserLesMotsDePasseAbsents() {
        UtilisateurDAO daoVide = new UtilisateurDAO() {
            @Override
            public boolean existeEmail(String email) {
                return false;
            }

            @Override
            public boolean existeMatricule(String matricule) {
                return false;
            }

            @Override
            public void insert(Utilisateur u) {
                fail("Aucun compte ne doit être créé sans mot de passe valide.");
            }

            @Override
            public Utilisateur findByEmailOuMatricule(String identifiant) {
                return null;
            }

            @Override
            public void mettreAJourTentatives(
                    int id, int tentatives, boolean verrouille) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Utilisateur> findByRole(Role role) {
                return List.of();
            }

            @Override
            public void changerActivation(int id, boolean actif) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void changerMotDePasse(int id, String hash, String sel) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void update(Utilisateur u) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(int id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Utilisateur findById(int id) {
                return null;
            }

            @Override
            public List<Utilisateur> findAll() {
                return List.of();
            }
        };

        AuthService service = new AuthService(daoVide);

        assertThrows(
                IllegalStateException.class,
                service::initialiserAdminParDefaut);
    }
}
