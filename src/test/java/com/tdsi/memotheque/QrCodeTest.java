package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.tdsi.memotheque.util.QrCode;

/**
 * Encodeur QR : comparaison module par module avec des matrices de référence produites par une
 * implémentation indépendante (bibliothèque Python « qrcode »), et contrôles de structure.
 */
class QrCodeTest {

    private static String reference(String nom) throws Exception {
        try (InputStream in = QrCodeTest.class.getResourceAsStream("/qr/" + nom)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void comparer(String texte, QrCode.Correction ecc, int masque, int version, String fichier) throws Exception {
        QrCode q = QrCode.encoder(texte.getBytes(StandardCharsets.UTF_8), ecc, masque);
        assertEquals(version, q.version);
        assertEquals(version * 4 + 17, q.taille);
        assertEquals(reference(fichier), q.enTexte(), "matrice différente de la référence " + fichier);
    }

    @Test
    void version1AvecAccents() throws Exception {
        comparer("MémoTDSI", QrCode.Correction.M, 2, 1, "v1-M-m2.txt");
    }

    @Test
    void version7AvecInformationDeVersion() throws Exception {
        comparer("otpauth://totp/MemoTDSI:admin%40mcs?secret=JBSWY3DPEHPK3PXP&issuer=MemoTDSI",
                QrCode.Correction.Q, 7, 7, "v7-Q-m7.txt");
    }

    @Test
    void version8BlocsEntrelaces() throws Exception {
        comparer("MTDSI1|K7F2|CONVOCATION|05/11/2026 10:30|Babacar NDIAYE|Candidat(e)|" + "x".repeat(60),
                QrCode.Correction.M, 5, 8, "v8-M-m5.txt");
    }

    @Test
    void choixDeLaVersionSelonLaCapacite() {
        // Capacités en mode octet, niveau M : v1 = 14 octets, v2 = 26, v10 = 213, v40 = 2331.
        assertEquals(1, QrCode.encoder(new byte[14], QrCode.Correction.M, -1).version);
        assertEquals(2, QrCode.encoder(new byte[15], QrCode.Correction.M, -1).version);
        assertEquals(10, QrCode.encoder(new byte[213], QrCode.Correction.M, -1).version);
        assertEquals(40, QrCode.encoder(new byte[2331], QrCode.Correction.M, -1).version);
        assertThrows(IllegalArgumentException.class, () -> QrCode.encoder(new byte[2332], QrCode.Correction.M, -1));
    }

    @Test
    void reperesDePositionAuxTroisCoins() {
        QrCode q = QrCode.encoder("Université Cheikh Anta Diop de Dakar");
        int n = q.taille;
        for (int[] c : new int[][] {{0, 0}, {n - 7, 0}, {0, n - 7}}) {
            for (int i = 0; i < 7; i++) {
                assertTrue(q.noir(c[0] + i, c[1]) && q.noir(c[0] + i, c[1] + 6), "bord du repère");
                assertTrue(q.noir(c[0], c[1] + i) && q.noir(c[0] + 6, c[1] + i), "bord du repère");
            }
            assertTrue(q.noir(c[0] + 3, c[1] + 3), "centre du repère");
            assertFalse(q.noir(c[0] + 1, c[1] + 1), "anneau blanc");
        }
        assertTrue(q.noir(8, n - 8), "module noir obligatoire");
        assertTrue(q.masque >= 0 && q.masque < 8);
    }
}
