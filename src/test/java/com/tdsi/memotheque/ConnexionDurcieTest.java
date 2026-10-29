package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.service.CompteService;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.PasswordUtil;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Validator;

/** Connexion durcie : anti-énumération, verrou temporaire, re-hachage, réinitialisation, inactivité. */
class ConnexionDurcieTest {

    @TempDir
    Path dossier;

    private AuthService auth;
    private UtilisateurDAOImpl dao;
    private Utilisateur etudiant;
    private Utilisateur admin;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        auth = new AuthService();
        dao = new UtilisateurDAOImpl();
        etudiant = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.L2, true);
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private String messageEchec(String identifiant, String mdp) {
        return assertThrows(IllegalArgumentException.class, () -> auth.connecter(identifiant, mdp)).getMessage();
    }

    @Test
    void memeMessagePourCompteInexistantEtMauvaisMotDePasse() {
        String inconnu = messageEchec("personne@test.sn", "MotDePasse123");
        String mauvais = messageEchec("etu@test.sn", "mauvais-mdp-1");
        assertEquals(inconnu, mauvais);
        assertEquals("Identifiants incorrects.", inconnu);
    }

    @Test
    void lEtatDuCompteNestReveleQuAvecLeBonMotDePasse() throws Exception {
        Utilisateur inactif = BaseDeTest.creerUtilisateur("inactif@test.sn", "ETU-0002",
                Role.ETUDIANT, Niveau.L1, false);

        assertEquals("Identifiants incorrects.", messageEchec("inactif@test.sn", "mauvais-mdp-1"));
        assertEquals("Ce compte n'est pas encore activé.",
                messageEchec("inactif@test.sn", BaseDeTest.MOT_DE_PASSE));
        assertFalse(SessionManager.estConnecte());
        assertNotNull(inactif);
    }

    @Test
    void verrouTemporaireLeveAutomatiquementApres15Minutes() throws Exception {
        for (int i = 0; i < 5; i++) {
            messageEchec("etu@test.sn", "mauvais-mdp-1");
        }
        Utilisateur lu = dao.findById(etudiant.getId());
        assertTrue(lu.isVerrouille());
        assertNotNull(lu.getDateVerrouillage());

        // Pendant le verrou : bon mot de passe refusé avec un message explicite, mauvais = message générique.
        assertTrue(messageEchec("etu@test.sn", BaseDeTest.MOT_DE_PASSE).contains("Réessayez dans"));
        assertEquals("Identifiants incorrects.", messageEchec("etu@test.sn", "mauvais-mdp-1"));
        assertEquals(5, dao.findById(etudiant.getId()).getTentativesEchec(), "pas de compteur pendant le verrou");

        // On simule l'écoulement des 15 minutes.
        poserDateVerrouillage(LocalDateTime.now().minusMinutes(16));
        Utilisateur connecte = auth.connecter("etu@test.sn", BaseDeTest.MOT_DE_PASSE);

        assertEquals(etudiant.getId(), connecte.getId());
        Utilisateur apres = dao.findById(etudiant.getId());
        assertFalse(apres.isVerrouille());
        assertEquals(0, apres.getTentativesEchec());
        assertNull(apres.getDateVerrouillage());
    }

    @Test
    void verrouNonExpireResteActif() throws Exception {
        for (int i = 0; i < 5; i++) {
            messageEchec("etu@test.sn", "mauvais-mdp-1");
        }
        poserDateVerrouillage(LocalDateTime.now().minusMinutes(10));
        assertTrue(messageEchec("etu@test.sn", BaseDeTest.MOT_DE_PASSE).contains("verrouillé"));
    }

    @Test
    void ancienHashRecalculeALaConnexion() throws Exception {
        // Ancien format M2 : PBKDF2 120 000 itérations, Base64 brut sans préfixe.
        String sel = PasswordUtil.genererSel();
        byte[] ancien = PasswordUtil.deriver("AncienMdp2024".toCharArray(), Base64.getDecoder().decode(sel),
                120_000, 256);
        dao.changerMotDePasse(etudiant.getId(), Base64.getEncoder().encodeToString(ancien), sel);
        assertTrue(PasswordUtil.besoinRehash(dao.findById(etudiant.getId()).getMotDePasseHash()));

        auth.connecter("etu@test.sn", "AncienMdp2024");

        Utilisateur lu = dao.findById(etudiant.getId());
        assertTrue(lu.getMotDePasseHash().startsWith("pbkdf2-sha256$"));
        assertFalse(PasswordUtil.besoinRehash(lu.getMotDePasseHash()));
        assertTrue(PasswordUtil.verifier("AncienMdp2024", lu.getSel(), lu.getMotDePasseHash()));
    }

    @Test
    void motsDePasseFaiblesRefuses() {
        for (String faible : new String[] {"abcdefgh", "12345678", "Password123", "azerty123"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> auth.inscrire("TDSI-9000", "Ba", "Awa", "awa@test.sn", faible, Niveau.L1), faible);
            assertEquals(AuthService.MESSAGE_MDP_FAIBLE, e.getMessage());
        }
        assertTrue(Validator.motDePasseRobuste("Cryptographie2026"));
    }

    @Test
    void reinitialisationParLadminPuisChangementObligatoire() throws Exception {
        CompteService comptes = new CompteService();
        SessionManager.ouvrir(admin);
        String temporaire = comptes.reinitialiserMotDePasse(etudiant.getId());
        SessionManager.fermer();

        assertTrue(Validator.motDePasseRobuste(temporaire));
        assertEquals(14, temporaire.length());
        assertEquals(1, Journal.compter("REINITIALISATION_MOT_DE_PASSE"));

        // L'ancien mot de passe ne marche plus, le temporaire oui, avec obligation de changer.
        assertEquals("Identifiants incorrects.", messageEchec("etu@test.sn", BaseDeTest.MOT_DE_PASSE));
        Utilisateur connecte = auth.connecter("etu@test.sn", temporaire);
        assertTrue(connecte.isDoitChangerMotDePasse());

        comptes.changerMotDePasse(temporaire, "NouveauMdp2026");
        assertFalse(SessionManager.getUtilisateur().isDoitChangerMotDePasse());
        assertFalse(dao.findById(etudiant.getId()).isDoitChangerMotDePasse());
    }

    @Test
    void reinitialisationReserveeALadminEtPasSurSoiMeme() throws Exception {
        CompteService comptes = new CompteService();
        SessionManager.ouvrir(etudiant);
        assertThrows(SecurityException.class, () -> comptes.reinitialiserMotDePasse(admin.getId()));

        SessionManager.ouvrir(admin);
        assertThrows(IllegalArgumentException.class, () -> comptes.reinitialiserMotDePasse(admin.getId()));
    }

    @Test
    void reinitialisationDeverrouilleLeCompte() throws Exception {
        dao.mettreAJourTentatives(etudiant.getId(), 5, true);
        SessionManager.ouvrir(admin);
        new CompteService().reinitialiserMotDePasse(etudiant.getId());

        Utilisateur lu = dao.findById(etudiant.getId());
        assertFalse(lu.isVerrouille());
        assertEquals(0, lu.getTentativesEchec());
    }

    @Test
    void detectionDInactivite() {
        Instant maintenant = Instant.now();
        assertFalse(SessionManager.estInactiveDepuis(Duration.ofMinutes(15), maintenant.plusSeconds(3600)),
                "sans session, rien à expirer");

        SessionManager.ouvrir(etudiant);
        assertFalse(SessionManager.estInactiveDepuis(Duration.ofMinutes(15), Instant.now().plusSeconds(60)));
        assertTrue(SessionManager.estInactiveDepuis(Duration.ofMinutes(15), Instant.now().plusSeconds(16 * 60)));
    }

    @Test
    void formatDuHashEtHashMalForme() {
        String sel = PasswordUtil.genererSel();
        String hash = PasswordUtil.hasher("MotDePasse123", sel);
        assertTrue(hash.matches("pbkdf2-sha256\\$\\d+\\$[A-Za-z0-9+/=]+"));
        assertFalse(PasswordUtil.verifier("MotDePasse123", sel, "pbkdf2-sha256$abc$xyz"));
        assertFalse(PasswordUtil.verifier("MotDePasse123", sel, "pas-du-base64-!!"));
        assertFalse(PasswordUtil.verifier("MotDePasse123", null, hash));
    }

    private void poserDateVerrouillage(LocalDateTime date) throws Exception {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE utilisateur SET date_verrouillage = ? WHERE id = ?")) {
            ps.setString(1, date.withNano(0).toString());
            ps.setInt(2, etudiant.getId());
            ps.executeUpdate();
        }
    }
}
