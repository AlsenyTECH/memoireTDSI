package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.CleSignatureDAOImpl;
import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.ValidationDAOImpl;
import com.tdsi.memotheque.model.CleSignature;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Mention;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.CompteService;
import com.tdsi.memotheque.service.SignatureService;
import com.tdsi.memotheque.service.SignatureService.Etat;
import com.tdsi.memotheque.service.SoutenanceService;
import com.tdsi.memotheque.service.ValidationService;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.SessionManager;

/** Signature Ed25519 du procès-verbal de soutenance (apposée par la scolarité à la publication). */
class SignatureTest {

    @TempDir
    Path dossier;

    private SignatureService signatures;
    private MemoireDAOImpl memoireDAO;
    private CleSignatureDAOImpl cleDAO;
    private Utilisateur etudiant;
    private Utilisateur encadreur;
    private Utilisateur admin;
    private Memoire memoire;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        signatures = new SignatureService();
        memoireDAO = new MemoireDAOImpl();
        cleDAO = new CleSignatureDAOImpl();
        etudiant = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.M2, true);
        encadreur = BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true);
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
        memoire = BaseDeTest.deposer(dossier, etudiant, encadreur, "2024-2025", Niveau.M2);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private void sql(String requete, Object... params) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(requete)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    private Memoire deuxiemeMemoire(String login) throws Exception {
        Utilisateur etu = BaseDeTest.creerUtilisateur(login + "@test.sn", "ETU-" + login.toUpperCase(),
                Role.ETUDIANT, Niveau.L3, true);
        return BaseDeTest.deposer(dossier, etu, encadreur, "2024-2025", Niveau.L3);
    }

    @Test
    void publicationProduitUnPvSigne() throws Exception {
        Memoire lu = BaseDeTest.publier(memoire, encadreur, admin, 15.5);

        assertEquals(StatutMemoire.VALIDE, lu.getStatut());
        assertEquals(Mention.BIEN, lu.getMention());
        assertEquals(15.5, lu.getNote());
        assertTrue(lu.getSignature().startsWith("v2:"));
        assertEquals(64, Base64.getDecoder().decode(lu.getSignature().substring(3)).length);

        SignatureService.Verification v = signatures.verifier(memoire.getId());
        assertEquals(Etat.VALIDE, v.etat(), v.message());
        assertEquals(admin.getNomComplet(), v.signataire());
        assertTrue(v.message().contains("procès-verbal"));
    }

    @Test
    void mauvaisMotDePasseNiSignatureNiPublication() throws Exception {
        SessionManager.ouvrir(encadreur);
        new ValidationService().donnerAvisFavorable(memoire.getId());
        Utilisateur pres = BaseDeTest.creerUtilisateur("p@test.sn", null, Role.ENCADREUR, null, true);
        Utilisateur exa = BaseDeTest.creerUtilisateur("x@test.sn", null, Role.ENCADREUR, null, true);
        SessionManager.ouvrir(admin);
        SoutenanceService s = new SoutenanceService();
        s.planifier(memoire.getId(), java.time.LocalDateTime.now().plusDays(2).withHour(9).withMinute(0), "A1",
                pres.getId(), java.util.List.of(exa.getId()));
        BaseDeTest.soutenanceDansLePasse(memoire.getId());

        assertThrows(SecurityException.class,
                () -> s.enregistrerProcesVerbal(memoire.getId(), 14, null, null, null, "mauvais-mdp-1"));
        assertEquals(StatutMemoire.AUTORISE, memoireDAO.findById(memoire.getId()).getStatut());
        assertNull(cleDAO.findActive(admin.getId()), "aucune clé créée sans authentification");
        assertEquals(1, Journal.compter("ECHEC_SIGNATURE"));
    }

    @Test
    void falsificationDuTitreDuPdfOuDuPvDetectee() throws Exception {
        BaseDeTest.publier(memoire, encadreur, admin, 12);

        sql("UPDATE memoire SET mention = 'TRES_BIEN' WHERE id = ?", memoire.getId());
        assertEquals(Etat.INVALIDE, signatures.verifier(memoire.getId()).etat(), "mention gonflée");

        sql("UPDATE memoire SET mention = 'ASSEZ_BIEN', note = 17 WHERE id = ?", memoire.getId());
        assertEquals(Etat.INVALIDE, signatures.verifier(memoire.getId()).etat(), "note modifiée");

        sql("UPDATE memoire SET note = 12, date_soutenance = '2020-01-01' WHERE id = ?", memoire.getId());
        assertEquals(Etat.INVALIDE, signatures.verifier(memoire.getId()).etat(), "date de soutenance");

        sql("UPDATE memoire SET date_soutenance = date('now', 'localtime') WHERE id = ?", memoire.getId());
        assertEquals(Etat.VALIDE, signatures.verifier(memoire.getId()).etat(), "retour à l'original");

        sql("UPDATE memoire SET titre = 'Titre modifié en douce' WHERE id = ?", memoire.getId());
        assertEquals(Etat.INVALIDE, signatures.verifier(memoire.getId()).etat(), "titre");
    }

    @Test
    void remplacementDuPdfOuDuSignataireDetecte() throws Exception {
        BaseDeTest.publier(memoire, encadreur, admin, 13);
        sql("UPDATE memoire SET empreinte_sha256 = ? WHERE id = ?", "f".repeat(64), memoire.getId());
        assertEquals(Etat.INVALIDE, signatures.verifier(memoire.getId()).etat());

        Memoire autre = deuxiemeMemoire("e2");
        BaseDeTest.publier(autre, encadreur, admin, 14);
        sql("UPDATE memoire SET id_validateur = ? WHERE id = ?", encadreur.getId(), autre.getId());
        assertEquals(Etat.INVALIDE, signatures.verifier(autre.getId()).etat(),
                "la clé n'appartient pas au signataire enregistré");
    }

    @Test
    void signatureRecopieeSurUnAutreMemoireRefusee() throws Exception {
        Memoire autre = deuxiemeMemoire("e2");
        Memoire signe = BaseDeTest.publier(memoire, encadreur, admin, 15);

        sql("UPDATE memoire SET statut = 'VALIDE', id_validateur = ?, date_publication = date('now','localtime'), "
                + "mention = 'BIEN', note = 15, date_soutenance = date('now','localtime'), "
                + "signature = ?, id_cle_signature = ? WHERE id = ?",
                admin.getId(), signe.getSignature(), signe.getIdCleSignature(), autre.getId());

        assertEquals(Etat.INVALIDE, signatures.verifier(autre.getId()).etat());
    }

    @Test
    void laClePriveeEstChiffreeEtReutilisee() throws Exception {
        Memoire autre = deuxiemeMemoire("e2");
        BaseDeTest.publier(memoire, encadreur, admin, 15);
        BaseDeTest.publier(autre, encadreur, admin, 11);

        assertEquals(memoireDAO.findById(memoire.getId()).getIdCleSignature(),
                memoireDAO.findById(autre.getId()).getIdCleSignature(), "une seule clé par signataire");

        CleSignature cle = cleDAO.findActive(admin.getId());
        byte[] chiffree = Base64.getDecoder().decode(cle.getClePriveeChiffree());
        assertEquals(48 + 12 + 16, chiffree.length, "PKCS#8 Ed25519 (48 o) + IV + tag GCM");
    }

    @Test
    void changementDeMotDePasseRechiffreLaCle() throws Exception {
        BaseDeTest.publier(memoire, encadreur, admin, 15);
        int idCle = cleDAO.findActive(admin.getId()).getId();

        SessionManager.ouvrir(admin);
        new CompteService().changerMotDePasse(BaseDeTest.MOT_DE_PASSE, "NouveauSecret2026");
        SessionManager.fermer();

        // L'outil publier() utilise MOT_DE_PASSE : on publie le second mémoire à la main.
        Memoire autre = deuxiemeMemoire("e2");
        SessionManager.ouvrir(encadreur);
        new ValidationService().donnerAvisFavorable(autre.getId());
        SessionManager.ouvrir(admin);
        SoutenanceService s = new SoutenanceService();
        s.planifier(autre.getId(), java.time.LocalDateTime.now().plusDays(3).withHour(14).withMinute(0), "B2",
                new com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl().findByEmailOuMatricule("president.jury@test.sn").getId(),
                java.util.List.of(new com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl()
                        .findByEmailOuMatricule("examinateur.jury@test.sn").getId()));
        BaseDeTest.soutenanceDansLePasse(autre.getId());
        s.enregistrerProcesVerbal(autre.getId(), 12, null, null, null, "NouveauSecret2026");

        assertEquals(idCle, memoireDAO.findById(autre.getId()).getIdCleSignature(), "même clé, re-chiffrée");
        assertEquals(Etat.VALIDE, signatures.verifier(memoire.getId()).etat());
        assertEquals(Etat.VALIDE, signatures.verifier(autre.getId()).etat());
    }

    @Test
    void ancienneSignatureV1ToujoursVerifiable() throws Exception {
        // Mémoire publié par l'ancienne version (validation signée par l'encadreur, format v1).
        new ValidationDAOImpl().valider(memoire.getId(), encadreur.getId());
        Memoire m = memoireDAO.findById(memoire.getId());

        KeyPair paire = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        CleSignature cle = new CleSignature();
        cle.setIdUtilisateur(encadreur.getId());
        cle.setClePublique(Base64.getEncoder().encodeToString(paire.getPublic().getEncoded()));
        cle.setClePriveeChiffree("inutile");
        cle.setSel("AAAAAAAAAAAAAAAAAAAAAA==");
        cle.setIterations(10_000);
        cleDAO.insert(cle);

        Method v1 = SignatureService.class.getDeclaredMethod("message", int.class, String.class, String.class,
                int.class, String.class, String.class, int.class, LocalDate.class);
        v1.setAccessible(true);
        byte[] message = (byte[]) v1.invoke(null, m.getId(), m.getEmpreinteSha256(), m.getTitre(),
                m.getAuteur().getId(), m.getAnneeAcademique(), m.getNiveau().name(), encadreur.getId(),
                m.getDatePublication());
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(paire.getPrivate());
        s.update(message);
        sql("UPDATE memoire SET signature = ?, id_cle_signature = ? WHERE id = ?",
                Base64.getEncoder().encodeToString(s.sign()), cle.getId(), m.getId());

        SignatureService.Verification v = signatures.verifier(m.getId());
        assertEquals(Etat.VALIDE, v.etat(), v.message());
        assertTrue(v.message().contains("validé par"));
    }

    @Test
    void memoirePublieSansSignatureSignaleCommeAbsente() throws Exception {
        new ValidationDAOImpl().valider(memoire.getId(), encadreur.getId());
        SignatureService.Verification v = signatures.verifier(memoire.getId());
        assertEquals(Etat.ABSENTE, v.etat());
    }
}
