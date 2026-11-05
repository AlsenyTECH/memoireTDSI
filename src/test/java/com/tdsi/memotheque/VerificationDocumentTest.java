package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.SoutenanceDAOImpl;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.DocumentEmis;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Soutenance;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.ConvocationService;
import com.tdsi.memotheque.service.ConvocationService.Convocation;
import com.tdsi.memotheque.service.DonneesDemo;
import com.tdsi.memotheque.service.VerificationService;
import com.tdsi.memotheque.service.VerificationService.Etat;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.QrCode;
import com.tdsi.memotheque.util.SessionManager;

/** Documents vérifiables : QR code signé Ed25519, code imprimé, registre, documents périmés ou falsifiés. */
class VerificationDocumentTest {

    @TempDir
    Path dossier;

    private VerificationService service;
    private int idBabs;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        new DonneesDemo().charger();
        service = new VerificationService();
        Utilisateur babs = new UtilisateurDAOImpl().findByEmailOuMatricule("babs@mcs");
        idBabs = new MemoireDAOImpl().findByAuteur(babs.getId()).get(0).getId();
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private void en(String email) throws Exception {
        SessionManager.ouvrir(new UtilisateurDAOImpl().findByEmailOuMatricule(email));
    }

    private Convocation convocationEtudiant() throws Exception {
        en("admin@mcs");
        return new ConvocationService().convocations(idBabs).get(0);
    }

    @Test
    void convocationAuthentiqueParQrEtParCode() throws Exception {
        DocumentEmis d = convocationEtudiant().document();
        assertNotNull(d);
        assertTrue(d.code().matches("[A-Z2-7]{16}"));
        String qr = VerificationService.texteQr(d);
        assertTrue(qr.startsWith("MTDSI1|" + d.code() + "|CONVOCATION|"));
        assertTrue(QrCode.encoder(qr).version <= 15, "QR lisible au téléphone : " + QrCode.encoder(qr).version);

        SessionManager.fermer();   // la vérification se fait sans compte
        VerificationService.Resultat r = service.verifier(qr);
        assertEquals(Etat.AUTHENTIQUE, r.etat(), r.message());
        assertTrue(r.champs().stream().anyMatch(c -> c[0].equals("Destinataire") && c[1].equals("Babacar NDIAYE")));
        assertEquals(Etat.AUTHENTIQUE, service.verifier(d.codeLisible()).etat());
        assertEquals(Etat.AUTHENTIQUE, service.verifier(d.code().toLowerCase()).etat());
    }

    @Test
    void reimpressionGardeLeMemeCode() throws Exception {
        String c1 = convocationEtudiant().document().code();
        String c2 = convocationEtudiant().document().code();
        assertEquals(c1, c2);
        List<Convocation> toutes = new ConvocationService().convocations(idBabs);
        assertEquals(toutes.size(), toutes.stream().map(c -> c.document().code()).distinct().count(),
                "un code distinct par destinataire");
    }

    @Test
    void qrFalsifieDetecte() throws Exception {
        String qr = VerificationService.texteQr(convocationEtudiant().document());
        String change = qr.replace("Babacar NDIAYE", "Babacar NDIAYF");
        assertNotEquals(qr, change);
        assertEquals(Etat.INVALIDE, service.verifier(change).etat());
        // Signature tronquée ou remplacée.
        assertEquals(Etat.INVALIDE, service.verifier(qr.substring(0, qr.length() - 2) + "AA").etat());
        assertEquals(Etat.INVALIDE, service.verifier("MTDSI1|n'importe quoi").etat());
    }

    @Test
    void convocationPerimeeApresDeplacementDeLaSoutenance() throws Exception {
        DocumentEmis d = convocationEtudiant().document();
        modifier("UPDATE soutenance SET salle = 'Amphi C' WHERE id_memoire = ?", idBabs);
        VerificationService.Resultat r = service.verifier(VerificationService.texteQr(d));
        assertEquals(Etat.PERIME, r.etat(), r.message());
        assertTrue(r.message().contains("déplacée"));
        // La nouvelle convocation porte un nouveau code, valable.
        DocumentEmis nouvelle = convocationEtudiant().document();
        assertNotEquals(d.code(), nouvelle.code());
        assertEquals(Etat.AUTHENTIQUE, service.verifier(nouvelle.codeLisible()).etat());
    }

    @Test
    void registreAltereDetecte() throws Exception {
        DocumentEmis d = convocationEtudiant().document();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE document_emis SET contenu = replace(contenu, 'Amphi', 'Salle') WHERE code = ?")) {
            ps.setString(1, d.code());
            assertEquals(1, ps.executeUpdate());
        }
        assertEquals(Etat.INVALIDE, service.verifier(d.code()).etat());
        assertEquals(Etat.INVALIDE, service.verifier(VerificationService.texteQr(d)).etat(),
                "le QR (original) ne correspond plus au registre");
    }

    @Test
    void codeInconnuOuMalForme() throws Exception {
        assertEquals(Etat.INCONNU, service.verifier("AAAA-BBBB-CCCC-DDDD").etat());
        assertEquals(Etat.INCONNU, service.verifier("123").etat());
        assertEquals(Etat.INCONNU, service.verifier("").etat());
    }

    @Test
    void pvEtAttestation() throws Exception {
        en("admin@mcs");
        Soutenance tenue = new SoutenanceDAOImpl().findAll().stream()
                .filter(s -> s.getStatut() == Soutenance.Statut.TENUE).findFirst().orElseThrow();
        Memoire m = new MemoireDAOImpl().findById(tenue.getIdMemoire());
        DocumentEmis pv = service.pv(m, tenue);
        assertEquals(Etat.AUTHENTIQUE, service.verifier(VerificationService.texteQr(pv)).etat());
        modifier("UPDATE memoire SET note = 3 WHERE id = ?", m.getId());
        assertEquals(Etat.PERIME, service.verifier(pv.code()).etat(), "note modifiée après émission");

        Memoire depot = new MemoireDAOImpl().findById(idBabs);
        en("babs@mcs");
        assertEquals(Etat.AUTHENTIQUE, service.verifier(service.attestationDepot(depot).code()).etat());
        en("samb@mcs");
        assertThrows(SecurityException.class, () -> service.attestationDepot(depot));
        SessionManager.fermer();
        assertThrows(SecurityException.class, () -> service.pv(m, tenue));
    }

    @Test
    void clePubliquePublieeEtStable() throws Exception {
        String cle = service.clePubliqueBase64();
        assertNotNull(cle);
        assertEquals(cle, new VerificationService().clePubliqueBase64());
        assertFalse(service.empreinteCle().isBlank());
        // La clé privée n'est jamais stockée en clair.
        String privee = new com.tdsi.memotheque.dao.impl.ParametreDAOImpl().lire("etablissement.cle_privee");
        assertFalse(privee.startsWith("MC4CAQAwBQYDK2Vw"), "PKCS#8 Ed25519 en clair");
    }

    private static void modifier(String sql, int id) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
