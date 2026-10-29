package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.JournalDAOImpl;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.VerificationJournal;
import com.tdsi.memotheque.service.JournalService;
import com.tdsi.memotheque.util.CsvUtil;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.SessionManager;

/** Journal chaîné par HMAC : toute altération est détectée. */
class JournalChaineTest {

    @TempDir
    Path dossier;

    private JournalService service;
    private JournalDAOImpl dao;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        service = new JournalService();
        dao = new JournalDAOImpl();
        for (int i = 1; i <= 5; i++) {
            service.log(null, "ACTION_" + i, "détail " + i);
        }
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private void sql(String requete) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(requete);
        }
    }

    @Test
    void journalIntact() throws Exception {
        VerificationJournal v = dao.verifierChaine();
        assertTrue(v.intact(), v.message());
        assertEquals(5, v.lignesVerifiees());
        assertTrue(dao.derniers(1).get(0).getHmac().matches("[0-9a-f]{64}"));
    }

    @Test
    void modificationDUneLigneDetectee() throws Exception {
        sql("UPDATE journal_activite SET details = 'falsifié' WHERE action = 'ACTION_3'");
        VerificationJournal v = dao.verifierChaine();
        assertFalse(v.intact());
        assertEquals(3, v.premiereLigneInvalide());
    }

    @Test
    void suppressionAuMilieuDetectee() throws Exception {
        sql("DELETE FROM journal_activite WHERE action = 'ACTION_2'");
        VerificationJournal v = dao.verifierChaine();
        assertFalse(v.intact());
        assertEquals(3, v.premiereLigneInvalide(), "la ligne suivante ne se raccroche plus à la chaîne");
    }

    @Test
    void suppressionDesDernieresLignesDetecteeParLAncre() throws Exception {
        sql("DELETE FROM journal_activite WHERE action IN ('ACTION_4', 'ACTION_5')");
        VerificationJournal v = dao.verifierChaine();
        assertFalse(v.intact());
        assertNull(v.premiereLigneInvalide());
        assertTrue(v.message().contains("ancre"));
    }

    @Test
    void ligneAjouteeALaMainDetectee() throws Exception {
        sql("INSERT INTO journal_activite (action, details, date_heure) VALUES ('FAUX', 'x', '2026-01-01 00:00:00')");
        assertFalse(dao.verifierChaine().intact());

        sql("DELETE FROM journal_activite WHERE action = 'FAUX'");
        sql("INSERT INTO journal_activite (action, details, date_heure, hmac) VALUES ('FAUX', 'x', "
                + "'2026-01-01 00:00:00', '" + "0".repeat(64) + "')");
        assertFalse(dao.verifierChaine().intact());
    }

    @Test
    void ancreFalsifieeDetectee() throws Exception {
        Path ancre = dossier.resolve("memotdsi-test.p12.journal-ancre");
        String contenu = Files.readString(ancre, StandardCharsets.US_ASCII);
        Files.writeString(ancre, contenu.replaceFirst("^5:", "4:"), StandardCharsets.US_ASCII);
        assertFalse(dao.verifierChaine().intact());
    }

    @Test
    void lignesAnterieuresAuChainageComptees() throws Exception {
        BaseDeTest.fermer();
        Path autre = Files.createDirectory(dossier.resolve("autre"));
        BaseDeTest.ouvrirBaseTemporaire(autre);
        sql("INSERT INTO journal_activite (action, details, date_heure) VALUES ('ANCIEN', 'x', '2025-01-01 10:00:00')");
        new JournalService().log(null, "NOUVEAU", "y");

        VerificationJournal v = new JournalDAOImpl().verifierChaine();
        assertTrue(v.intact(), v.message());
        assertEquals(1, v.lignesNonProtegees());
        assertEquals(1, v.lignesVerifiees());
    }

    @Test
    void verificationReserveeALadmin() throws Exception {
        assertThrows(SecurityException.class, () -> service.verifierIntegrite());

        SessionManager.ouvrir(BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true));
        assertTrue(service.verifierIntegrite().intact());
        assertEquals(1, Journal.compter("VERIFICATION_JOURNAL"));
        assertTrue(dao.verifierChaine().intact(), "la ligne de vérification prolonge la chaîne");
    }

    @Test
    void csvProtegeContreLInjectionDeFormules() throws Exception {
        // Apostrophe ajoutée devant « = », puis guillemets doublés car la cellule en contient.
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", CsvUtil.cellule("=HYPERLINK(\"x\")"));
        assertEquals("'+1", CsvUtil.cellule("+1"));
        assertEquals("'@cmd", CsvUtil.cellule("@cmd"));
        assertEquals("\"a;b\"", CsvUtil.cellule("a;b"));
        assertEquals("", CsvUtil.cellule(null));
        assertEquals("titre;12", CsvUtil.ligne("titre", 12));

        Path csv = dossier.resolve("t.csv");
        CsvUtil.ecrire(csv, new Object[] {"A", "B"}, List.<Object[]>of(new Object[] {"é", "-2"}));
        String lu = Files.readString(csv, StandardCharsets.UTF_8);
        assertEquals("﻿A;B\r\né;'-2\r\n", lu);
    }
}
