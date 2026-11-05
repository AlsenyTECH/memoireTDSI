package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.tdsi.memotheque.util.PdfExport;

/** Structure du PDF produit : en-tête, objets aux positions annoncées par la table xref, pages A4. */
class PdfExportTest {

    @Test
    void tableXrefCoherente() throws Exception {
        BufferedImage img = new BufferedImage(64, 90, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", jpg);
        Method m = PdfExport.class.getDeclaredMethod("assembler", String.class, List.class, List.class);
        m.setAccessible(true);
        byte[] pdf = (byte[]) m.invoke(null, "Convocations à la soutenance",
                List.of(jpg.toByteArray(), jpg.toByteArray()), List.of(new int[] {64, 90}, new int[] {64, 90}));
        String t = new String(pdf, StandardCharsets.ISO_8859_1);

        assertTrue(t.startsWith("%PDF-1.4"));
        assertTrue(t.endsWith("%%EOF\n"));
        assertEquals(2, t.split("/Type /Page ", -1).length - 1, "deux pages");
        assertTrue(t.contains("/MediaBox [0 0 595.28 841.89]"));
        assertTrue(t.contains("/Filter /DCTDecode"));

        Matcher sx = Pattern.compile("startxref\n(\\d+)\n").matcher(t);
        assertTrue(sx.find());
        int xref = Integer.parseInt(sx.group(1));
        assertTrue(t.startsWith("xref", xref));
        Matcher e = Pattern.compile("(\\d{10}) 00000 n ").matcher(t.substring(xref));
        int numero = 1;
        while (e.find()) {
            int pos = Integer.parseInt(e.group(1));
            assertTrue(t.startsWith(numero + " 0 obj", pos), "objet " + numero + " à la position " + pos);
            numero++;
        }
        assertEquals(1 + 2 + 3 * 2 + 1, numero, "catalogue, pages, infos et 3 objets par page");
    }
}
