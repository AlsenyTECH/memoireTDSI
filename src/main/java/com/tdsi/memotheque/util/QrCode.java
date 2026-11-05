package com.tdsi.memotheque.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Encodeur de QR code (ISO/IEC 18004), sans dépendance externe.
 * Mode octet (UTF-8), versions 1 à 40, correction d'erreurs au choix (M par défaut : ~15 % du code
 * peut être abîmé), choix automatique du masque par le calcul de pénalité de la norme.
 * Utilisé pour les codes de vérification des documents et l'activation de la double authentification.
 */
public final class QrCode {

    /** Niveaux de correction d'erreurs et leurs bits de format. */
    public enum Correction {
        L(1), M(0), Q(3), H(2);

        final int bitsFormat;

        Correction(int b) {
            bitsFormat = b;
        }
    }

    public final int version;
    public final int taille;
    public final Correction correction;
    public final int masque;
    private final boolean[][] modules;     // [y][x], vrai = module noir
    private final boolean[][] fonction;    // modules réservés (repères, format...)

    // ------------------------------------------------------------------ construction

    /** Encode un texte (UTF-8) avec le niveau M. */
    public static QrCode encoder(String texte) {
        return encoder(texte.getBytes(StandardCharsets.UTF_8), Correction.M, -1);
    }

    /** Encode des octets ; masque -1 = choix automatique, sinon 0 à 7. */
    public static QrCode encoder(byte[] donnees, Correction ecc, int masque) {
        int version = 1;
        int bitsUtiles;
        for (;; version++) {
            if (version > 40) {
                throw new IllegalArgumentException("Données trop longues pour un QR code (" + donnees.length + " octets).");
            }
            bitsUtiles = nbOctetsDonnees(version, ecc) * 8;
            if (4 + bitsCompteur(version) + donnees.length * 8 <= bitsUtiles) {
                break;
            }
        }
        // Flux de bits : mode octet (0100), longueur, données, terminateur, bourrage.
        Bits b = new Bits();
        b.ajouter(0b0100, 4);
        b.ajouter(donnees.length, bitsCompteur(version));
        for (byte o : donnees) {
            b.ajouter(o & 0xFF, 8);
        }
        b.ajouter(0, Math.min(4, bitsUtiles - b.n));
        b.ajouter(0, (8 - b.n % 8) % 8);
        for (int pad = 0xEC; b.n < bitsUtiles; pad ^= 0xEC ^ 0x11) {
            b.ajouter(pad, 8);
        }
        byte[] mots = new byte[b.n / 8];
        for (int i = 0; i < b.n; i++) {
            mots[i >>> 3] |= (byte) ((b.bits[i] ? 1 : 0) << (7 - (i & 7)));
        }
        return new QrCode(version, ecc, ajouterCorrection(mots, version, ecc), masque);
    }

    private QrCode(int version, Correction ecc, byte[] motsFinaux, int masqueDemande) {
        this.version = version;
        this.correction = ecc;
        this.taille = version * 4 + 17;
        this.modules = new boolean[taille][taille];
        this.fonction = new boolean[taille][taille];
        dessinerMotifs();
        placerMots(motsFinaux);

        int choisi = masqueDemande;
        if (choisi < 0) {
            int meilleur = Integer.MAX_VALUE;
            for (int m = 0; m < 8; m++) {
                appliquerMasque(m);
                dessinerFormat(m);
                int p = penalite();
                if (p < meilleur) {
                    meilleur = p;
                    choisi = m;
                }
                appliquerMasque(m);    // XOR : on revient à l'état initial
            }
        }
        this.masque = choisi;
        appliquerMasque(choisi);
        dessinerFormat(choisi);
    }

    /** Vrai si le module (x, y) est noir. */
    public boolean noir(int x, int y) {
        return x >= 0 && y >= 0 && x < taille && y < taille && modules[y][x];
    }

    /** Image du QR code avec sa marge blanche réglementaire de 4 modules. */
    public WritableImage image(int pixelsParModule) {
        int marge = 4;
        int cote = (taille + 2 * marge) * pixelsParModule;
        WritableImage img = new WritableImage(cote, cote);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < cote; y++) {
            for (int x = 0; x < cote; x++) {
                boolean n = noir(x / pixelsParModule - marge, y / pixelsParModule - marge);
                pw.setArgb(x, y, n ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return img;
    }

    /** Vue nette (sans lissage) du QR code, à la taille demandée en pixels. */
    public ImageView vue(double cote) {
        ImageView v = new ImageView(image(8));
        v.setFitWidth(cote);
        v.setFitHeight(cote);
        v.setSmooth(false);
        return v;
    }

    // ------------------------------------------------------------------ motifs fixes

    private void dessinerMotifs() {
        for (int i = 0; i < taille; i++) {
            poser(6, i, i % 2 == 0);
            poser(i, 6, i % 2 == 0);
        }
        reperer(3, 3);
        reperer(taille - 4, 3);
        reperer(3, taille - 4);

        int[] pos = positionsAlignement(version);
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!(i == 0 && j == 0 || i == 0 && j == n - 1 || i == n - 1 && j == 0)) {
                    aligner(pos[i], pos[j]);
                }
            }
        }
        dessinerFormat(0);          // réserve les emplacements, réécrits à la fin
        if (version >= 7) {
            int rem = version;
            for (int i = 0; i < 12; i++) {
                rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            }
            int bits = version << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean bit = ((bits >>> i) & 1) != 0;
                int a = taille - 11 + i % 3;
                int c = i / 3;
                poser(a, c, bit);
                poser(c, a, bit);
            }
        }
    }

    private void dessinerFormat(int m) {
        int donnees = correction.bitsFormat << 3 | m;
        int rem = donnees;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = (donnees << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) {
            poser(8, i, bit(bits, i));
        }
        poser(8, 7, bit(bits, 6));
        poser(8, 8, bit(bits, 7));
        poser(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) {
            poser(14 - i, 8, bit(bits, i));
        }
        for (int i = 0; i < 8; i++) {
            poser(taille - 1 - i, 8, bit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            poser(8, taille - 15 + i, bit(bits, i));
        }
        poser(8, taille - 8, true);     // module noir obligatoire
    }

    private void reperer(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int d = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx;
                int y = cy + dy;
                if (x >= 0 && x < taille && y >= 0 && y < taille) {
                    poser(x, y, d != 2 && d != 4);
                }
            }
        }
    }

    private void aligner(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                poser(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private void poser(int x, int y, boolean n) {
        modules[y][x] = n;
        fonction[y][x] = true;
    }

    static int[] positionsAlignement(int version) {
        if (version == 1) {
            return new int[0];
        }
        int n = version / 7 + 2;
        int pas = (version * 8 + n * 3 + 5) / (n * 4 - 4) * 2;
        int[] r = new int[n];
        r[0] = 6;
        for (int i = n - 1, p = version * 4 + 17 - 7; i >= 1; i--, p -= pas) {
            r[i] = p;
        }
        return r;
    }

    // ------------------------------------------------------------------ données

    private void placerMots(byte[] mots) {
        int i = 0;
        for (int droite = taille - 1; droite >= 1; droite -= 2) {
            if (droite == 6) {
                droite = 5;     // la colonne de synchronisation est sautée
            }
            for (int v = 0; v < taille; v++) {
                for (int j = 0; j < 2; j++) {
                    int x = droite - j;
                    boolean monte = ((droite + 1) & 2) == 0;
                    int y = monte ? taille - 1 - v : v;
                    if (!fonction[y][x] && i < mots.length * 8) {
                        modules[y][x] = bit(mots[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void appliquerMasque(int m) {
        for (int y = 0; y < taille; y++) {
            for (int x = 0; x < taille; x++) {
                boolean inverser = switch (m) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    case 7 -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                    default -> throw new IllegalArgumentException("Masque 0 à 7.");
                };
                if (inverser && !fonction[y][x]) {
                    modules[y][x] = !modules[y][x];
                }
            }
        }
    }

    /** Pénalité de la norme (règles 1 à 4) : plus elle est basse, plus le code se lit facilement. */
    private int penalite() {
        int p = 0;
        for (int sens = 0; sens < 2; sens++) {
            for (int a = 0; a < taille; a++) {
                int serie = 1;
                for (int b = 1; b < taille; b++) {
                    if (lire(sens, a, b) == lire(sens, a, b - 1)) {
                        serie++;
                        if (serie == 5) {
                            p += 3;
                        } else if (serie > 5) {
                            p++;
                        }
                    } else {
                        serie = 1;
                    }
                }
                // Règle 3 : motif 1011101 bordé de 4 modules clairs d'un côté.
                for (int b = 0; b + 10 < taille; b++) {
                    if (motifRepere(sens, a, b)) {
                        p += 40;
                    }
                }
            }
        }
        for (int y = 0; y + 1 < taille; y++) {
            for (int x = 0; x + 1 < taille; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    p += 3;
                }
            }
        }
        int noirs = 0;
        for (boolean[] ligne : modules) {
            for (boolean c : ligne) {
                noirs += c ? 1 : 0;
            }
        }
        int total = taille * taille;
        int k = (Math.abs(noirs * 20 - total * 10) + total - 1) / total - 1;
        return p + Math.max(0, k) * 10;
    }

    private boolean lire(int sens, int a, int b) {
        return sens == 0 ? modules[a][b] : modules[b][a];
    }

    private static final boolean[] MOTIF_A = {true, false, true, true, true, false, true, false, false, false, false};
    private static final boolean[] MOTIF_B = {false, false, false, false, true, false, true, true, true, false, true};

    private boolean motifRepere(int sens, int a, int b) {
        boolean okA = true;
        boolean okB = true;
        for (int i = 0; i < 11; i++) {
            boolean c = lire(sens, a, b + i);
            okA &= c == MOTIF_A[i];
            okB &= c == MOTIF_B[i];
        }
        return okA || okB;
    }

    // ------------------------------------------------------------------ Reed-Solomon et blocs

    private static final int[][] ECC_PAR_BLOC = {
        // L
        {-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
        // M
        {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},
        // Q
        {-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
        // H
        {-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
    };

    private static final int[][] NB_BLOCS = {
        {-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},
        {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},
        {-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},
        {-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},
    };

    static int nbModulesDonnees(int v) {
        int r = (16 * v + 128) * v + 64;
        if (v >= 2) {
            int n = v / 7 + 2;
            r -= (25 * n - 10) * n - 55;
            if (v >= 7) {
                r -= 36;
            }
        }
        return r;
    }

    static int nbOctetsDonnees(int v, Correction ecc) {
        return nbModulesDonnees(v) / 8 - ECC_PAR_BLOC[ecc.ordinal()][v] * NB_BLOCS[ecc.ordinal()][v];
    }

    private static int bitsCompteur(int version) {
        return version <= 9 ? 8 : 16;
    }

    private static byte[] ajouterCorrection(byte[] donnees, int v, Correction ecc) {
        int nbBlocs = NB_BLOCS[ecc.ordinal()][v];
        int lgEcc = ECC_PAR_BLOC[ecc.ordinal()][v];
        int total = nbModulesDonnees(v) / 8;
        int courts = nbBlocs - total % nbBlocs;
        int lgCourt = total / nbBlocs;

        byte[][] blocs = new byte[nbBlocs][];
        byte[] diviseur = diviseurRs(lgEcc);
        for (int i = 0, k = 0; i < nbBlocs; i++) {
            int lgDonnees = lgCourt - lgEcc + (i < courts ? 0 : 1);
            byte[] dat = Arrays.copyOfRange(donnees, k, k + lgDonnees);
            k += lgDonnees;
            byte[] bloc = Arrays.copyOf(dat, lgCourt + 1);
            byte[] reste = resteRs(dat, diviseur);
            System.arraycopy(reste, 0, bloc, bloc.length - lgEcc, lgEcc);
            blocs[i] = bloc;
        }
        byte[] r = new byte[total];
        for (int i = 0, k = 0; i < blocs[0].length; i++) {
            for (int j = 0; j < blocs.length; j++) {
                // Les blocs courts ont un octet fictif à la position lgCourt - lgEcc : on le saute.
                if (i != lgCourt - lgEcc || j >= courts) {
                    r[k++] = blocs[j][i];
                }
            }
        }
        return r;
    }

    private static byte[] diviseurRs(int degre) {
        byte[] r = new byte[degre];
        r[degre - 1] = 1;
        int racine = 1;
        for (int i = 0; i < degre; i++) {
            for (int j = 0; j < r.length; j++) {
                r[j] = (byte) multiplier(r[j] & 0xFF, racine);
                if (j + 1 < r.length) {
                    r[j] ^= r[j + 1];
                }
            }
            racine = multiplier(racine, 0x02);
        }
        return r;
    }

    private static byte[] resteRs(byte[] donnees, byte[] diviseur) {
        byte[] r = new byte[diviseur.length];
        for (byte b : donnees) {
            int facteur = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) {
                r[i] ^= (byte) multiplier(diviseur[i] & 0xFF, facteur);
            }
        }
        return r;
    }

    /** Multiplication dans GF(2^8) modulo x^8 + x^4 + x^3 + x^2 + 1. */
    private static int multiplier(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    /** Petit tampon de bits. */
    private static final class Bits {
        boolean[] bits = new boolean[64];
        int n;

        void ajouter(int valeur, int longueur) {
            for (int i = longueur - 1; i >= 0; i--) {
                if (n == bits.length) {
                    bits = Arrays.copyOf(bits, n * 2);
                }
                bits[n++] = ((valeur >>> i) & 1) != 0;
            }
        }
    }

    /** Représentation texte (tests) : « # » noir, « . » blanc. */
    public String enTexte() {
        StringBuilder sb = new StringBuilder();
        for (boolean[] ligne : modules) {
            for (boolean c : ligne) {
                sb.append(c ? '#' : '.');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
