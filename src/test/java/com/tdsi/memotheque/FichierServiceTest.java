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
import com.tdsi.memotheque.util.HashUtil;

/** [M3] Tests de sécurité du contrôle et du stockage des PDF. */
class FichierServiceTest {

    @TempDir
    Path dossier;

    private FichierService service() {
        return new FichierService(dossier.resolve("storage"));
    }

    @Test
    void pdfValideStockeSousNomUuidAvecSha256() throws Exception {
        Path source = BaseDeTest.creerPdf(dossier, "Mon memoire 2025.pdf");
        Memoire m = new Memoire();

        service().stocker(source, m);

        Path stocke = Path.of(m.getCheminFichier());
        assertTrue(Files.exists(stocke));
        assertEquals(dossier.resolve("storage"), stocke.getParent());
        assertTrue(stocke.getFileName().toString()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf"));
        assertEquals("Mon memoire 2025.pdf", m.getNomOriginal());
        assertEquals(Files.size(source), m.getTailleOctets());
        assertEquals(HashUtil.sha256(source), m.getEmpreinteSha256());
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
