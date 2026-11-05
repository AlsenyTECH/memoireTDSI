package com.tdsi.memotheque.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;

/**
 * Export PDF sans bibliothèque externe : chaque page (lettre, PV, attestation) est rendue en haute
 * définition (environ 220 points par pouce), compressée en JPEG et placée sur une page A4.
 * Le fichier produit est un PDF 1.4 standard, lisible par tous les lecteurs.
 * Limite assumée : le texte est une image (non sélectionnable), ce qui convient à des documents officiels.
 */
public final class PdfExport {

    private static final double A4_L = 595.28;
    private static final double A4_H = 841.89;
    private static final double ECHELLE = 2.5;
    private static final float QUALITE_JPEG = 0.9f;

    private PdfExport() { }

    /** Écrit les pages dans un PDF (à appeler sur le thread JavaFX : rendu des nœuds). */
    public static void ecrire(Path fichier, String titre, List<? extends Node> pages) throws IOException {
        List<byte[]> jpegs = new ArrayList<>();
        List<int[]> tailles = new ArrayList<>();
        for (Node n : pages) {
            BufferedImage img = rendre(n);
            jpegs.add(jpeg(img));
            tailles.add(new int[] {img.getWidth(), img.getHeight()});
        }
        byte[] pdf = assembler(titre, jpegs, tailles);
        Path tmp = Files.createTempFile(fichier.toAbsolutePath().getParent(), "memotdsi", ".pdf.tmp");
        try {
            Files.write(tmp, pdf);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Rendu d'un nœud hors écran, avec la feuille de style de l'application. */
    static BufferedImage rendre(Node n) {
        if (n.getScene() == null) {
            Scene sc = new Scene(new Group(n));
            java.net.URL css = PdfExport.class.getResource("/css/style.css");
            if (css != null) {
                sc.getStylesheets().add(css.toExternalForm());
            }
        }
        if (n instanceof Parent p) {
            p.applyCss();
            p.layout();
        }
        javafx.scene.effect.Effect effet = n.getEffect();
        n.setEffect(null);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setTransform(Transform.scale(ECHELLE, ECHELLE));
        WritableImage w = n.snapshot(sp, null);
        n.setEffect(effet);
        int lw = (int) w.getWidth();
        int lh = (int) w.getHeight();
        BufferedImage bi = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_RGB);
        PixelReader pr = w.getPixelReader();
        int[] ligne = new int[lw];
        for (int y = 0; y < lh; y++) {
            pr.getPixels(0, y, lw, 1, javafx.scene.image.PixelFormat.getIntArgbInstance(), ligne, 0, lw);
            for (int x = 0; x < lw; x++) {
                int a = ligne[x] >>> 24;
                // Fond transparent -> blanc.
                if (a < 255) {
                    int r = ((ligne[x] >> 16) & 0xFF) * a / 255 + 255 - a;
                    int g = ((ligne[x] >> 8) & 0xFF) * a / 255 + 255 - a;
                    int b = (ligne[x] & 0xFF) * a / 255 + 255 - a;
                    ligne[x] = (r << 16) | (g << 8) | b;
                }
            }
            bi.setRGB(0, y, lw, 1, ligne, 0, lw);
        }
        return bi;
    }

    private static byte[] jpeg(BufferedImage img) throws IOException {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            w.setOutput(ios);
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(QUALITE_JPEG);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
        return out.toByteArray();
    }

    /** Structure PDF : catalogue, arbre des pages, une image et un flux de contenu par page, table xref. */
    static byte[] assembler(String titre, List<byte[]> jpegs, List<int[]> tailles) throws IOException {
        Sortie s = new Sortie();
        int n = jpegs.size();
        // Numérotation : 1 catalogue, 2 pages, 3 infos, puis pour chaque page : page, contenu, image.
        int total = 3 + 3 * n;
        long[] positions = new long[total + 1];

        s.ecrire("%PDF-1.4\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1));
        positions[1] = s.n;
        s.texte("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        positions[2] = s.n;
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) {
            kids.append(4 + 3 * i).append(" 0 R ");
        }
        s.texte("2 0 obj\n<< /Type /Pages /Kids [ " + kids + "] /Count " + n + " >>\nendobj\n");
        positions[3] = s.n;
        s.texte("3 0 obj\n<< /Title " + chaine(titre) + " /Producer " + chaine("MémoTDSI — UCAD")
                + " /CreationDate (D:" + java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ") >>\nendobj\n");

        for (int i = 0; i < n; i++) {
            int page = 4 + 3 * i;
            int contenu = page + 1;
            int image = page + 2;
            int largeur = tailles.get(i)[0];
            int hauteur = tailles.get(i)[1];
            // Image ajustée à la page (marges de 0), centrée horizontalement, calée en haut.
            double e = Math.min(A4_L / largeur, A4_H / hauteur);
            double w = largeur * e;
            double h = hauteur * e;
            double x = (A4_L - w) / 2;
            double y = A4_H - h;
            String flux = String.format(Locale.ROOT, "q %.2f 0 0 %.2f %.2f %.2f cm /Im0 Do Q\n", w, h, x, y);

            positions[page] = s.n;
            s.texte(page + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595.28 841.89] "
                    + "/Resources << /XObject << /Im0 " + image + " 0 R >> >> /Contents " + contenu + " 0 R >>\nendobj\n");
            positions[contenu] = s.n;
            s.texte(contenu + " 0 obj\n<< /Length " + flux.length() + " >>\nstream\n" + flux + "endstream\nendobj\n");
            positions[image] = s.n;
            s.texte(image + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + largeur + " /Height " + hauteur
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpegs.get(i).length
                    + " >>\nstream\n");
            s.ecrire(jpegs.get(i));
            s.texte("\nendstream\nendobj\n");
        }

        long xref = s.n;
        StringBuilder t = new StringBuilder("xref\n0 " + (total + 1) + "\n0000000000 65535 f \n");
        for (int i = 1; i <= total; i++) {
            t.append(String.format(Locale.ROOT, "%010d 00000 n \n", positions[i]));
        }
        t.append("trailer\n<< /Size ").append(total + 1).append(" /Root 1 0 R /Info 3 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        s.texte(t.toString());
        return s.out.toByteArray();
    }

    /** Chaîne PDF en UTF-16BE (accents corrects dans les propriétés du document). */
    private static String chaine(String texte) {
        StringBuilder sb = new StringBuilder("<FEFF");
        for (byte b : (texte == null ? "" : texte).getBytes(StandardCharsets.UTF_16BE)) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.append('>').toString();
    }

    private static final class Sortie {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        long n;

        void texte(String t) throws IOException {
            ecrire(t.getBytes(StandardCharsets.US_ASCII));
        }

        void ecrire(byte[] b) throws IOException {
            out.write(b);
            n += b.length;
        }
    }
}
