package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.FichierService;
import com.tdsi.memotheque.service.JournalService;
import com.tdsi.memotheque.service.SauvegardeService;
import com.tdsi.memotheque.util.GestionCles;
import com.tdsi.memotheque.util.SessionManager;

/** Sauvegarde chiffrée : aller-retour, mot de passe, falsification, zip slip, droits. */
class SauvegardeTest {

    private static final String MDP = "SauvegardeTDSI2026";

    @TempDir
    Path dossier;

    private SauvegardeService service;
    private Utilisateur admin;
    private Memoire memoire;
    private Path archive;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        service = new SauvegardeService(new JournalService(), dossier.resolve("storage"));
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
        Utilisateur etu = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.M2, true);
        Utilisateur enc = BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true);
        memoire = BaseDeTest.deposer(dossier, etu, enc, "2024-2025", Niveau.M2);
        archive = dossier.resolve("test" + SauvegardeService.EXTENSION);
        SessionManager.ouvrir(admin);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    @Test
    void allerRetourComplet() throws Exception {
        int n = service.exporter(archive, MDP);
        assertTrue(n >= 3, "base + PDF + coffre");

        byte[] octets = Files.readAllBytes(archive);
        assertEquals("MTDSAVE1", new String(octets, 0, 8, StandardCharsets.US_ASCII));
        String brut = new String(octets, StandardCharsets.ISO_8859_1);
        assertFalse(brut.contains("SQLite format"), "la base n'apparaît pas en clair");
        assertFalse(brut.contains("MANIFEST"), "même les noms de fichiers sont chiffrés");

        Path cible = dossier.resolve("restauration");
        SauvegardeService.Restauration r = service.restaurer(archive, MDP, cible);
        assertEquals(n, r.fichiers());
        assertTrue(Files.exists(cible.resolve("memotdsi.db")));
        assertTrue(Files.exists(cible.resolve("coffre/memotdsi-test.p12")));
        assertEquals(1, Journal.compter("SAUVEGARDE"));

        // La base restaurée contient le mémoire...
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + cible.resolve("memotdsi.db"));
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT titre, chemin_fichier FROM memoire")) {
            assertTrue(rs.next());
            assertEquals(memoire.getTitre(), rs.getString(1));
        }
        // ... et le PDF restauré se déchiffre avec le coffre restauré.
        String nomPdf = Path.of(memoire.getCheminFichier()).getFileName().toString();
        Memoire copie = new Memoire();
        copie.setCheminFichier(cible.resolve("storage").resolve(nomPdf).toString());
        copie.setEmpreinteSha256(memoire.getEmpreinteSha256());
        GestionCles.utiliserPourTests(cible.resolve("coffre"));
        assertEquals(memoire.getTailleOctets(),
                new FichierService(cible.resolve("storage")).lireContenuVerifie(copie).length);
    }

    @Test
    void mauvaisMotDePasseRienNEstEcrit() throws Exception {
        service.exporter(archive, MDP);
        Path cible = dossier.resolve("vide");
        assertThrows(SecurityException.class, () -> service.restaurer(archive, "MauvaisMotDePasse99", cible));
        assertFalse(Files.exists(cible.resolve("memotdsi.db")));
    }

    @Test
    void archiveModifieeRefusee() throws Exception {
        service.exporter(archive, MDP);
        byte[] octets = Files.readAllBytes(archive);
        octets[octets.length / 2] ^= 0x01;
        Files.write(archive, octets);
        assertThrows(SecurityException.class, () -> service.restaurer(archive, MDP, dossier.resolve("r")));
    }

    @Test
    void enTeteAuthentifie() throws Exception {
        service.exporter(archive, MDP);
        byte[] octets = Files.readAllBytes(archive);
        octets[8 + 16 + 3] ^= 0x01;   // nombre d'itérations PBKDF2 modifié
        Files.write(archive, octets);
        assertThrows(SecurityException.class, () -> service.restaurer(archive, MDP, dossier.resolve("r")));
    }

    @Test
    void dossierCibleNonVideRefuse() throws Exception {
        service.exporter(archive, MDP);
        Path cible = Files.createDirectory(dossier.resolve("occupe"));
        Files.writeString(cible.resolve("existant.txt"), "x");
        assertThrows(IllegalArgumentException.class, () -> service.restaurer(archive, MDP, cible));
    }

    @Test
    void zipSlipBloque() throws Exception {
        // Archive malveillante correctement chiffrée qui tente d'écrire hors du dossier cible.
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(zip)) {
            z.putNextEntry(new ZipEntry("../evasion.txt"));
            z.write("pirate".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        Method chiffrer = SauvegardeService.class.getDeclaredMethod("chiffrerVers", Path.class, byte[].class, String.class);
        chiffrer.setAccessible(true);
        chiffrer.invoke(service, archive, zip.toByteArray(), MDP);

        Path cible = dossier.resolve("cible");
        assertThrows(SecurityException.class, () -> service.restaurer(archive, MDP, cible));
        assertFalse(Files.exists(dossier.resolve("evasion.txt")));
    }

    @Test
    void motDePasseFaibleEtDroitsAdmin() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.exporter(archive, "court1"));
        assertThrows(IllegalArgumentException.class, () -> service.exporter(archive, "seulementdeslettres"));

        SessionManager.ouvrir(BaseDeTest.creerUtilisateur("x@test.sn", null, Role.ENCADREUR, null, true));
        assertThrows(SecurityException.class, () -> service.exporter(archive, MDP));
        assertFalse(Files.exists(archive));
    }

    @Test
    void fichierQuiNEstPasUneSauvegarde() throws Exception {
        Files.writeString(archive, "pas une sauvegarde");
        assertThrows(IllegalArgumentException.class, () -> service.restaurer(archive, MDP, dossier.resolve("r")));
    }
}
