package com.tdsi.memotheque.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Coffre de clés de l'application (fichier PKCS12 « memotdsi.p12 », hors de la base).
 *
 * Il contient deux clés secrètes de 256 bits, créées au premier besoin :
 *  - « journal-hmac »  : HMAC-SHA256 qui chaîne le journal d'activité ;
 *  - « fichiers-aes »  : AES-256-GCM qui chiffre les PDF dans storage/.
 *
 * Modèle de menace : une copie ou une modification de memotdsi.db / storage/ seule
 * ne permet ni de lire les PDF ni de réécrire le journal sans être détecté.
 *
 * Mot de passe du coffre : variable d'environnement MEMOTDSI_KEYSTORE_PASSWORD si elle existe,
 * sinon un mot de passe aléatoire est créé dans « memotdsi.p12.pass » (droits 600).
 */
public final class GestionCles {

    private static final Logger LOG = Logger.getLogger(GestionCles.class.getName());

    public static final String ALIAS_HMAC_JOURNAL = "journal-hmac";
    public static final String ALIAS_AES_FICHIERS = "fichiers-aes";

    private static final Path FICHIER_PAR_DEFAUT = Donnees.chemin("memotdsi.p12");

    private static Path fichier = FICHIER_PAR_DEFAUT;
    private static char[] motDePasseImpose;     // tests uniquement
    private static KeyStore coffre;
    private static char[] motDePasse;

    private GestionCles() { }

    public static synchronized SecretKey cleHmacJournal() {
        return obtenir(ALIAS_HMAC_JOURNAL, "HmacSHA256");
    }

    public static synchronized SecretKey cleChiffrementFichiers() {
        return obtenir(ALIAS_AES_FICHIERS, "AES");
    }

    /** Fichier du coffre (inclus dans les sauvegardes). */
    public static synchronized Path fichier() {
        return fichier;
    }

    /** Les tests utilisent un coffre jetable dans leur dossier temporaire. */
    public static synchronized void utiliserPourTests(Path dossier) {
        fichier = dossier.resolve("memotdsi-test.p12");
        motDePasseImpose = "mot-de-passe-de-test".toCharArray();
        coffre = null;
        motDePasse = null;
    }

    public static synchronized void utiliserParDefaut() {
        fichier = FICHIER_PAR_DEFAUT;
        motDePasseImpose = null;
        coffre = null;
        motDePasse = null;
    }

    private static SecretKey obtenir(String alias, String algorithme) {
        try {
            charger();
            KeyStore.Entry entree = coffre.getEntry(alias, new KeyStore.PasswordProtection(motDePasse));
            if (entree instanceof KeyStore.SecretKeyEntry secrete) {
                return secrete.getSecretKey();
            }

            KeyGenerator generateur = KeyGenerator.getInstance(algorithme);
            generateur.init(256, new SecureRandom());
            SecretKey cle = generateur.generateKey();

            coffre.setEntry(alias, new KeyStore.SecretKeyEntry(cle),
                    new KeyStore.PasswordProtection(motDePasse));
            enregistrer();
            LOG.info(() -> "Nouvelle clé « " + alias + " » créée dans " + fichier);
            return cle;

        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Coffre de clés inutilisable (" + fichier + ") : "
                    + e.getMessage(), e);
        }
    }

    private static void charger() throws GeneralSecurityException, IOException {
        if (coffre != null) {
            return;
        }
        motDePasse = lireMotDePasse();

        KeyStore ks = KeyStore.getInstance("PKCS12");
        if (Files.exists(fichier)) {
            try (InputStream in = Files.newInputStream(fichier)) {
                ks.load(in, motDePasse);
            }
        } else {
            ks.load(null, motDePasse);
        }
        coffre = ks;
    }

    /** Écriture atomique : fichier temporaire puis renommage. */
    private static void enregistrer() throws GeneralSecurityException, IOException {
        Path dossier = fichier.toAbsolutePath().getParent();
        Files.createDirectories(dossier);
        Path temporaire = Files.createTempFile(dossier, "memotdsi", ".p12.tmp");
        try (OutputStream out = Files.newOutputStream(temporaire)) {
            coffre.store(out, motDePasse);
        }
        restreindreDroits(temporaire);
        Files.move(temporaire, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static char[] lireMotDePasse() throws IOException {
        if (motDePasseImpose != null) {
            return motDePasseImpose.clone();
        }
        String env = System.getenv("MEMOTDSI_KEYSTORE_PASSWORD");
        if (env != null && !env.isBlank()) {
            return env.toCharArray();
        }

        Path fichierMdp = Path.of(fichier + ".pass");
        if (Files.exists(fichierMdp)) {
            return Files.readString(fichierMdp, StandardCharsets.US_ASCII).trim().toCharArray();
        }

        byte[] aleatoire = new byte[32];
        new SecureRandom().nextBytes(aleatoire);
        String nouveau = Base64.getEncoder().encodeToString(aleatoire);
        Files.writeString(fichierMdp, nouveau, StandardCharsets.US_ASCII);
        restreindreDroits(fichierMdp);
        LOG.warning(() -> "MEMOTDSI_KEYSTORE_PASSWORD absent : mot de passe du coffre généré dans "
                + fichierMdp.toAbsolutePath() + " (à protéger et à sauvegarder).");
        return nouveau.toCharArray();
    }

    private static void restreindreDroits(Path p) {
        try {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows : pas de droits POSIX, on garde les droits par défaut du dossier.
        }
    }
}
