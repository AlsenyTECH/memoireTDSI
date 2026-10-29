package com.tdsi.memotheque.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;

import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.CryptoUtil;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.GestionCles;
import com.tdsi.memotheque.util.Migrations;
import com.tdsi.memotheque.util.PasswordUtil;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Validator;

/**
 * Sauvegarde et restauration chiffrées (administrateur).
 *
 * Contenu (archive ZIP) : copie cohérente de la base (VACUUM INTO), PDF de storage/,
 * coffre de clés (sans lui les PDF chiffrés sont illisibles) et un MANIFEST avec le SHA-256
 * de chaque fichier.
 *
 * Format du fichier .mtdsave :
 *   "MTDSAVE1" (8 o) | sel (16 o) | itérations PBKDF2 (4 o) | IV (12 o) | ZIP chiffré AES-256-GCM + tag
 * L'en-tête complet est authentifié (données associées GCM) : on ne peut pas baisser le nombre
 * d'itérations ni modifier un octet sans que le déchiffrement échoue.
 *
 * Restauration : toujours dans un dossier NEUF (jamais d'écrasement de la base en service),
 * avec protection contre le « zip slip » et contrôle des SHA-256 du manifeste.
 */
public class SauvegardeService {

    public static final String EXTENSION = ".mtdsave";
    private static final byte[] MAGIC = "MTDSAVE1".getBytes(StandardCharsets.US_ASCII);
    private static final int TAILLE_ENTETE = MAGIC.length + 16 + 4 + CryptoUtil.TAILLE_IV;
    private static final long TAILLE_MAX_ARCHIVE = 2L * 1024 * 1024 * 1024;   // 2 Go
    private static final int LONGUEUR_MIN_MDP = 12;

    /** Résumé d'une restauration réussie. */
    public record Restauration(Path dossier, int fichiers, String dateSauvegarde, String manifeste) { }

    private final JournalService journalService;
    private final Path dossierStockage;

    public SauvegardeService() {
        this(new JournalService(), FichierService.dossierParDefaut());
    }

    public SauvegardeService(JournalService journalService, Path dossierStockage) {
        this.journalService = journalService;
        this.dossierStockage = dossierStockage;
    }

    // ------------------------------------------------------------------ export

    /** Crée la sauvegarde chiffrée. Retourne le nombre de fichiers sauvegardés. */
    public int exporter(Path destination, String motDePasse) throws IOException, SQLException {
        Utilisateur admin = exigerAdmin();
        verifierMotDePasse(motDePasse);

        Path travail = Files.createTempDirectory("memotdsi-sauvegarde");
        try {
            // 1. Copie cohérente de la base, même si l'application l'utilise.
            Path copieBase = travail.resolve("memotdsi.db");
            try (Connection c = DatabaseConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement("VACUUM INTO ?")) {
                ps.setString(1, copieBase.toString());
                ps.execute();
            }

            // 2. Liste des fichiers à archiver.
            Map<String, Path> fichiers = new LinkedHashMap<>();
            fichiers.put("memotdsi.db", copieBase);
            if (Files.isDirectory(dossierStockage)) {
                try (Stream<Path> pdfs = Files.list(dossierStockage)) {
                    pdfs.filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                        .sorted()
                        .forEach(p -> fichiers.put("storage/" + p.getFileName(), p));
                }
            }
            Path coffre = GestionCles.fichier();
            for (Path p : new Path[] {coffre, Path.of(coffre + ".pass"), Path.of(coffre + ".journal-ancre")}) {
                if (Files.isRegularFile(p)) {
                    fichiers.put("coffre/" + p.getFileName(), p);
                }
            }

            // 3. ZIP + manifeste (SHA-256 de chaque fichier), puis chiffrement.
            ByteArrayOutputStream zip = new ByteArrayOutputStream();
            StringBuilder manifeste = new StringBuilder()
                    .append("MemoTDSI sauvegarde\n")
                    .append("date=").append(LocalDateTime.now().withNano(0)).append('\n')
                    .append("schema=").append(Migrations.versionCourante()).append('\n');
            try (ZipOutputStream zos = new ZipOutputStream(zip)) {
                for (Map.Entry<String, Path> e : fichiers.entrySet()) {
                    byte[] contenu = Files.readAllBytes(e.getValue());
                    manifeste.append(CryptoUtil.sha256Hex(contenu)).append("  ").append(e.getKey()).append('\n');
                    zos.putNextEntry(new ZipEntry(e.getKey()));
                    zos.write(contenu);
                    zos.closeEntry();
                }
                zos.putNextEntry(new ZipEntry("MANIFEST.txt"));
                zos.write(manifeste.toString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            chiffrerVers(destination, zip.toByteArray(), motDePasse);
            journalService.log(admin.getId(), "SAUVEGARDE",
                    fichiers.size() + " fichier(s) sauvegardé(s) vers " + destination.getFileName());
            return fichiers.size();

        } finally {
            supprimerRecursif(travail);
        }
    }

    // ------------------------------------------------------------------ restauration

    /**
     * Déchiffre la sauvegarde et l'extrait dans un dossier neuf (vide ou inexistant).
     * Mot de passe faux ou fichier modifié : SecurityException, rien n'est écrit.
     */
    public Restauration restaurer(Path archive, String motDePasse, Path dossierCible)
            throws IOException {
        Utilisateur admin = exigerAdmin();

        if (Files.exists(dossierCible)) {
            try (Stream<Path> contenu = Files.list(dossierCible)) {
                if (contenu.findAny().isPresent()) {
                    throw new IllegalArgumentException("Choisissez un dossier vide pour la restauration.");
                }
            }
        }
        if (Files.size(archive) > TAILLE_MAX_ARCHIVE) {
            throw new IllegalArgumentException("Archive trop volumineuse.");
        }

        byte[] zip = dechiffrer(Files.readAllBytes(archive), motDePasse);

        // Première passe en mémoire : chemins sûrs + contrôle du manifeste AVANT toute écriture.
        Map<String, byte[]> entrees = new LinkedHashMap<>();
        Path racine = dossierCible.toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                Path cible = racine.resolve(e.getName()).normalize();
                if (!cible.startsWith(racine) || e.getName().contains("..")) {
                    throw new SecurityException("Archive refusée : chemin dangereux « " + e.getName() + " ».");
                }
                entrees.put(e.getName(), zis.readAllBytes());
            }
        }

        byte[] manifesteOctets = entrees.remove("MANIFEST.txt");
        if (manifesteOctets == null) {
            throw new SecurityException("Archive refusée : manifeste absent.");
        }
        String manifeste = new String(manifesteOctets, StandardCharsets.UTF_8);
        String date = "?";
        int lignesFichiers = 0;
        for (String ligne : manifeste.split("\n")) {
            if (ligne.startsWith("date=")) {
                date = ligne.substring(5);
            }
            String[] parties = ligne.split("  ", 2);
            if (parties.length == 2 && parties[0].matches("[0-9a-f]{64}")) {
                lignesFichiers++;
                byte[] contenu = entrees.get(parties[1]);
                if (contenu == null || !CryptoUtil.egal(parties[0], CryptoUtil.sha256Hex(contenu))) {
                    throw new SecurityException("Archive refusée : « " + parties[1] + " » ne correspond pas au manifeste.");
                }
            }
        }
        if (lignesFichiers != entrees.size()) {
            throw new SecurityException("Archive refusée : fichiers non déclarés dans le manifeste.");
        }

        for (Map.Entry<String, byte[]> e : entrees.entrySet()) {
            Path cible = racine.resolve(e.getKey()).normalize();
            Files.createDirectories(cible.getParent());
            Files.write(cible, e.getValue());
        }
        Files.write(racine.resolve("MANIFEST.txt"), manifesteOctets);

        journalService.log(admin.getId(), "RESTAURATION",
                entrees.size() + " fichier(s) extrait(s) de " + archive.getFileName() + " vers " + racine);
        return new Restauration(racine, entrees.size(), date, manifeste);
    }

    // ------------------------------------------------------------------ chiffrement

    private void chiffrerVers(Path destination, byte[] clair, String motDePasse) throws IOException {
        SecureRandom alea = new SecureRandom();
        byte[] sel = new byte[16];
        byte[] iv = new byte[CryptoUtil.TAILLE_IV];
        alea.nextBytes(sel);
        alea.nextBytes(iv);
        int iterations = PasswordUtil.ITERATIONS;

        byte[] entete = ByteBuffer.allocate(TAILLE_ENTETE).put(MAGIC).put(sel).putInt(iterations).put(iv).array();
        byte[] cle = PasswordUtil.deriver(motDePasse.toCharArray(), sel, iterations, 256);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, CryptoUtil.cleAes(cle), new GCMParameterSpec(CryptoUtil.TAILLE_TAG_BITS, iv));
            c.updateAAD(entete);

            Path temporaire = Files.createTempFile(destination.toAbsolutePath().getParent(), "sauvegarde", ".tmp");
            try {
                try (OutputStream out = Files.newOutputStream(temporaire)) {
                    out.write(entete);
                    try (CipherOutputStream cos = new CipherOutputStream(out, c)) {
                        cos.write(clair);
                    }
                }
                Files.move(temporaire, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporaire);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Chiffrement de la sauvegarde impossible", e);
        } finally {
            Arrays.fill(cle, (byte) 0);
        }
    }

    private byte[] dechiffrer(byte[] fichier, String motDePasse) throws IOException {
        if (fichier.length < TAILLE_ENTETE + 16
                || !Arrays.equals(fichier, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) {
            throw new IllegalArgumentException("Ce fichier n'est pas une sauvegarde MémoTDSI.");
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(fichier, MAGIC.length, TAILLE_ENTETE));
        byte[] sel = in.readNBytes(16);
        int iterations = in.readInt();
        byte[] iv = in.readNBytes(CryptoUtil.TAILLE_IV);
        if (iterations < 10_000 || iterations > 10_000_000) {
            throw new SecurityException("En-tête de sauvegarde invalide.");
        }

        byte[] cle = PasswordUtil.deriver(motDePasse == null ? new char[0] : motDePasse.toCharArray(),
                sel, iterations, 256);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, CryptoUtil.cleAes(cle), new GCMParameterSpec(CryptoUtil.TAILLE_TAG_BITS, iv));
            c.updateAAD(fichier, 0, TAILLE_ENTETE);
            return c.doFinal(fichier, TAILLE_ENTETE, fichier.length - TAILLE_ENTETE);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new SecurityException("Mot de passe incorrect ou sauvegarde modifiée.");
        } catch (GeneralSecurityException e) {
            throw new IOException("Déchiffrement de la sauvegarde impossible", e);
        } finally {
            Arrays.fill(cle, (byte) 0);
        }
    }

    // ------------------------------------------------------------------ outils

    private void verifierMotDePasse(String motDePasse) {
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN_MDP || !Validator.motDePasseRobuste(motDePasse)) {
            throw new IllegalArgumentException("Mot de passe de sauvegarde : au moins " + LONGUEUR_MIN_MDP
                    + " caractères avec lettres et chiffres (il protège TOUTE la plateforme).");
        }
    }

    private Utilisateur exigerAdmin() {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null || !SessionManager.aLeRole(Role.ADMIN)) {
            throw new SecurityException("Sauvegarde et restauration réservées à l'administrateur.");
        }
        return u;
    }

    private static void supprimerRecursif(Path dossier) {
        try (Stream<Path> tout = Files.walk(dossier)) {
            tout.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            // dossier temporaire : nettoyé par le système au pire
        }
    }
}
