package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.service.FichierService;
import com.tdsi.memotheque.util.GestionCles;
import com.tdsi.memotheque.util.HashUtil;

/** [M3] Tests de sécurité du contrôle et du stockage des PDF. */
class FichierServiceTest {

    @TempDir
    Path dossier;

    @org.junit.jupiter.api.BeforeEach
    void coffre() {
        GestionCles.utiliserPourTests(dossier);
    }

    @org.junit.jupiter.api.AfterEach
    void fermer() {
        GestionCles.utiliserParDefaut();
    }

    private FichierService service() {
        return new FichierService(dossier.resolve("storage"));
    }

    @Test
    void pdfValideStockeChiffreSousNomUuidAvecSha256() throws Exception {
        Path source = BaseDeTest.creerPdf(dossier, "Mon memoire 2025.pdf");
        Memoire m = new Memoire();

        service().stocker(source, m);

        Path stocke = Path.of(m.getCheminFichier());
        assertTrue(Files.exists(stocke));
        assertEquals(dossier.resolve("storage"), stocke.getParent());
        assertTrue(stocke.getFileName().toString()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf\\.enc"));
        assertEquals("Mon memoire 2025.pdf", m.getNomOriginal());
        assertEquals(Files.size(source), m.getTailleOctets());
        assertEquals(HashUtil.sha256(source), m.getEmpreinteSha256());

        // Chiffré sur le disque : ni la signature %PDF ni le texte ne sont lisibles.
        byte[] surDisque = Files.readAllBytes(stocke);
        assertFalse(new String(surDisque, StandardCharsets.ISO_8859_1).contains("%PDF"));
        assertEquals(Files.size(source) + 12 + 16, surDisque.length, "IV 12 octets + tag 16 octets");

        // Relu, déchiffré et vérifié : identique à l'original.
        assertArrayEquals(Files.readAllBytes(source), service().lireContenuVerifie(m));
    }

    @Test
    void fichierChiffreModifieDetecte() throws Exception {
        Memoire m = new Memoire();
        service().stocker(BaseDeTest.creerPdf(dossier, "a.pdf"), m);
        Path stocke = Path.of(m.getCheminFichier());

        byte[] octets = Files.readAllBytes(stocke);
        octets[40] ^= 1;   // un seul bit modifié
        Files.write(stocke, octets);

        assertThrows(SecurityException.class, () -> service().lireContenuVerifie(m));
    }

    @Test
    void fichierChiffreRenommeOuEchangeDetecte() throws Exception {
        Memoire a = new Memoire();
        Memoire b = new Memoire();
        service().stocker(BaseDeTest.creerPdf(dossier, "a.pdf"), a);
        service().stocker(BaseDeTest.creerPdf(dossier, "b.pdf"), b);

        // On remplace le fichier de A par celui de B : le nom (données associées GCM) ne correspond plus.
        Files.copy(Path.of(b.getCheminFichier()), Path.of(a.getCheminFichier()),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThrows(SecurityException.class, () -> service().lireContenuVerifie(a));
    }

    @Test
    void cheminHorsStockageRefuseEnLecture() throws Exception {
        Path ailleurs = BaseDeTest.creerPdf(dossier, "ailleurs.pdf");
        Memoire m = new Memoire();
        m.setCheminFichier(ailleurs.toString());
        m.setEmpreinteSha256(HashUtil.sha256(ailleurs));

        assertThrows(SecurityException.class, () -> service().lireContenuVerifie(m));
        m.setCheminFichier(dossier.resolve("storage/../ailleurs.pdf").toString());
        assertThrows(SecurityException.class, () -> service().lireContenuVerifie(m));
    }

    @Test
    void ancienFichierEnClairToujoursLisibleEtVerifie() throws Exception {
        Path storage = Files.createDirectories(dossier.resolve("storage"));
        Path clair = BaseDeTest.creerPdf(storage, "ancien.pdf");
        Memoire m = new Memoire();
        m.setCheminFichier(clair.toString());
        m.setEmpreinteSha256(HashUtil.sha256(clair));

        assertTrue(FichierService.estEnClair(m.getCheminFichier()));
        assertArrayEquals(Files.readAllBytes(clair), service().lireContenuVerifie(m));

        m.setEmpreinteSha256("0".repeat(64));
        assertThrows(SecurityException.class, () -> service().lireContenuVerifie(m));
    }

    @Test
    void extractionTemporairePrivee() throws Exception {
        Path source = BaseDeTest.creerPdf(dossier, "x.pdf");
        Memoire m = new Memoire();
        m.setId(7);
        service().stocker(source, m);

        Path extrait = service().extraireVersTemporaire(m);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(extrait));
        assertTrue(extrait.getFileName().toString().startsWith("memoire-7-"));
        assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(
                Files.getPosixFilePermissions(extrait.getParent())));
    }

    @Test
    void fauxPdfTexteRenommeEstRefuse() throws Exception {
        Path faux = dossier.resolve("faux.pdf");
        Files.writeString(faux, "Ceci est un simple fichier texte renommé en .pdf",
                StandardCharsets.UTF_8);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service().stocker(faux, new Memoire()));
        assertTrue(e.getMessage().contains("%PDF"));
        assertNoFichierStocke();
    }

    @Test
    void extensionAutreQuePdfRefusee() throws Exception {
        Path source = BaseDeTest.creerPdf(dossier, "memoire.docx");
        assertThrows(IllegalArgumentException.class, () -> service().verifierPdf(source));
    }

    @Test
    void fichierDePlusDe20MoRefuse() throws Exception {
        Path gros = dossier.resolve("gros.pdf");
        try (RandomAccessFile f = new RandomAccessFile(gros.toFile(), "rw")) {
            f.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
            f.setLength(FichierService.TAILLE_MAX_OCTETS + 1);
        }

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service().stocker(gros, new Memoire()));
        assertTrue(e.getMessage().contains("20 Mo"));
        assertNoFichierStocke();
    }

    @Test
    void fichierDeExactement20MoAccepte() throws Exception {
        Path limite = dossier.resolve("limite.pdf");
        try (RandomAccessFile f = new RandomAccessFile(limite.toFile(), "rw")) {
            f.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
            f.setLength(FichierService.TAILLE_MAX_OCTETS);
        }
        assertDoesNotThrow(() -> service().verifierPdf(limite));
    }

    @Test
    void fichierVideOuAbsentRefuse() throws Exception {
        Path vide = dossier.resolve("vide.pdf");
        Files.createFile(vide);
        assertThrows(IllegalArgumentException.class, () -> service().verifierPdf(vide));
        assertThrows(IllegalArgumentException.class,
                () -> service().verifierPdf(dossier.resolve("absent.pdf")));
        assertThrows(IllegalArgumentException.class, () -> service().verifierPdf(null));
    }

    @Test
    void suppressionHorsDuStockageRefusee() throws Exception {
        Path ailleurs = BaseDeTest.creerPdf(dossier, "ailleurs.pdf");
        FichierService s = service();

        assertThrows(SecurityException.class, () -> s.supprimer(ailleurs.toString()));
        assertThrows(SecurityException.class,
                () -> s.supprimer(s.getDossierStockage().resolve("../ailleurs.pdf").toString()));
        assertTrue(Files.exists(ailleurs));
    }

    @Test
    void suppressionDansLeStockage() throws Exception {
        Memoire m = new Memoire();
        FichierService s = service();
        s.stocker(BaseDeTest.creerPdf(dossier, "a.pdf"), m);

        s.supprimer(m.getCheminFichier());
        assertFalse(Files.exists(Path.of(m.getCheminFichier())));
    }

    private void assertNoFichierStocke() throws Exception {
        Path storage = dossier.resolve("storage");
        if (Files.exists(storage)) {
            try (var fichiers = Files.list(storage)) {
                assertEquals(0, fichiers.count(), "aucun fichier ne doit rester dans storage/");
            }
        }
    }
}
