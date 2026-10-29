package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.CompteService;
import com.tdsi.memotheque.service.SignatureService;
import com.tdsi.memotheque.service.SignatureService.Etat;
import com.tdsi.memotheque.service.ValidationService;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.SessionManager;

/** Signature Ed25519 des mémoires validés. */
class SignatureTest {

    @TempDir
    Path dossier;

    private ValidationService validation;
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
        validation = new ValidationService();
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

    private void validerParEncadreur(Memoire m) throws Exception {
        SessionManager.ouvrir(encadreur);
        validation.valider(m.getId(), BaseDeTest.MOT_DE_PASSE);
        SessionManager.fermer();
    }

    private void sql(String requete, Object... params) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(requete)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    @Test
    void validationProduitUneSignatureValide() throws Exception {
        validerParEncadreur(memoire);

        Memoire lu = memoireDAO.findById(memoire.getId());
        assertEquals(StatutMemoire.VALIDE, lu.getStatut());
        assertEquals(64, Base64.getDecoder().decode(lu.getSignature()).length, "signature Ed25519 = 64 octets");

        SignatureService.Verification v = signatures.verifier(memoire.getId());
        assertEquals(Etat.VALIDE, v.etat(), v.message());
        assertEquals(encadreur.getNomComplet(), v.signataire());
        assertTrue(v.empreinteCle().matches("[0-9a-f]{4}(:[0-9a-f]{4}){3}"));
    }

    @Test
    void mauvaisMotDePasseNiSignatureNiValidation() throws Exception {
        SessionManager.ouvrir(encadreur);
        assertThrows(SecurityException.class, () -> validation.valider(memoire.getId(), "mauvais-mdp-1"));
        assertEquals(StatutMemoire.EN_ATTENTE, memoireDAO.findById(memoire.getId()).getStatut());
        assertNull(cleDAO.findActive(encadreur.getId()), "aucune clé créée sans authentification");
        assertEquals(1, Journal.compter("ECHEC_SIGNATURE"));
    }

    @Test
    void modificationDuTitreEnBaseInvalideLaSignature() throws Exception {
        validerParEncadreur(memoire);
        sql("UPDATE memoire SET titre = 'Titre modifié en douce' WHERE id = ?", memoire.getId());
        assertEquals(Etat.INVALIDE, signatures.verifier(memoire.getId()).etat());
    }

    @Test
    void remplacementDuPdfInvalideLaSignature() throws Exception {
        validerParEncadreur(memoire);
        sql("UPDATE memoire SET empreinte_sha256 = ? WHERE id = ?", "f".repeat(64), memoire.getId());
        assertEquals(Etat.INVALIDE, signatures.verifier(memoire.getId()).etat());
    }

    @Test
    void changementDeValidateurOuDeDateInvalideLaSignature() throws Exception {
        validerParEncadreur(memoire);
        sql("UPDATE memoire SET date_publication = '2020-01-01' WHERE id = ?", memoire.getId());
        assertEquals(Etat.INVALIDE, signatures.verifier(memoire.getId()).etat());

        sql("UPDATE memoire SET date_publication = date('now'), id_validateur = ? WHERE id = ?",
                admin.getId(), memoire.getId());
        assertEquals(Etat.INVALIDE, signatures.verifier(memoire.getId()).etat(),
                "la clé n'appartient pas au validateur enregistré");
    }

    @Test
    void signatureRecopieeSurUnAutreMemoireRefusee() throws Exception {
        Utilisateur etu2 = BaseDeTest.creerUtilisateur("etu2@test.sn", "ETU-0002", Role.ETUDIANT, Niveau.L3, true);
        Memoire autre = BaseDeTest.deposer(dossier, etu2, encadreur, "2024-2025", Niveau.L3);
        validerParEncadreur(memoire);

        // L'autre mémoire est publié à la main avec la signature du premier.
        Memoire signe = memoireDAO.findById(memoire.getId());
        sql("UPDATE memoire SET statut = 'VALIDE', id_validateur = ?, date_publication = date('now'), "
                + "signature = ?, id_cle_signature = ? WHERE id = ?",
                encadreur.getId(), signe.getSignature(), signe.getIdCleSignature(), autre.getId());

        assertEquals(Etat.INVALIDE, signatures.verifier(autre.getId()).etat());
    }

    @Test
    void laClePriveeEstChiffreeEtReutilisee() throws Exception {
        Utilisateur etu2 = BaseDeTest.creerUtilisateur("etu2@test.sn", "ETU-0002", Role.ETUDIANT, Niveau.L3, true);
        Memoire autre = BaseDeTest.deposer(dossier, etu2, encadreur, "2024-2025", Niveau.L3);
        validerParEncadreur(memoire);
        validerParEncadreur(autre);

        assertEquals(memoireDAO.findById(memoire.getId()).getIdCleSignature(),
                memoireDAO.findById(autre.getId()).getIdCleSignature(), "une seule clé par validateur");

        CleSignature cle = cleDAO.findActive(encadreur.getId());
        byte[] chiffree = Base64.getDecoder().decode(cle.getClePriveeChiffree());
        assertEquals(48 + 12 + 16, chiffree.length, "PKCS#8 Ed25519 (48 o) + IV + tag GCM");
    }

    @Test
    void changementDeMotDePasseRechiffreLaCle() throws Exception {
        validerParEncadreur(memoire);
        int idCle = cleDAO.findActive(encadreur.getId()).getId();

        SessionManager.ouvrir(encadreur);
        new CompteService().changerMotDePasse(BaseDeTest.MOT_DE_PASSE, "NouveauSecret2026");

        Utilisateur etu2 = BaseDeTest.creerUtilisateur("etu2@test.sn", "ETU-0002", Role.ETUDIANT, Niveau.L3, true);
        Memoire autre = BaseDeTest.deposer(dossier, etu2, encadreur, "2024-2025", Niveau.L3);
        SessionManager.ouvrir(encadreur);
        validation.valider(autre.getId(), "NouveauSecret2026");

        assertEquals(idCle, memoireDAO.findById(autre.getId()).getIdCleSignature(), "même clé, re-chiffrée");
        assertEquals(Etat.VALIDE, signatures.verifier(memoire.getId()).etat());
        assertEquals(Etat.VALIDE, signatures.verifier(autre.getId()).etat());
    }

    @Test
    void reinitialisationParLadminRevoqueLaCleSansCasserLesAnciennesSignatures() throws Exception {
        validerParEncadreur(memoire);
        int ancienneCle = cleDAO.findActive(encadreur.getId()).getId();

        SessionManager.ouvrir(admin);
        String temporaire = new CompteService().reinitialiserMotDePasse(encadreur.getId());
        assertNull(cleDAO.findActive(encadreur.getId()));

        Utilisateur etu2 = BaseDeTest.creerUtilisateur("etu2@test.sn", "ETU-0002", Role.ETUDIANT, Niveau.L3, true);
        Memoire autre = BaseDeTest.deposer(dossier, etu2, encadreur, "2024-2025", Niveau.L3);
        SessionManager.ouvrir(encadreur);
        validation.valider(autre.getId(), temporaire);

        assertNotEquals(ancienneCle, (int) memoireDAO.findById(autre.getId()).getIdCleSignature());
        assertEquals(Etat.VALIDE, signatures.verifier(memoire.getId()).etat(), "ancienne clé publique conservée");
        assertEquals(Etat.VALIDE, signatures.verifier(autre.getId()).etat());
    }

    @Test
    void memoireValideSansSignatureSignaleCommeAbsente() throws Exception {
        new ValidationDAOImpl().valider(memoire.getId(), encadreur.getId());
        SignatureService.Verification v = signatures.verifier(memoire.getId());
        assertEquals(Etat.ABSENTE, v.etat());
        assertTrue(v.message().contains("avant l'activation"));
    }
}
