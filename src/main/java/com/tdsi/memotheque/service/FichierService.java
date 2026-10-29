package com.tdsi.memotheque.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.util.HashUtil;

/**
 * [M3] Contrôle et stockage des PDF.
 * À faire : extension .pdf, taille <= 20 Mo, signature « %PDF » dans les premiers octets,
 * copie dans le dossier storage/ sous un nom UUID (jamais le nom d'origine), calcul du SHA-256 (HashUtil).
 *
 * Les contrôles de taille et de signature sont refaits sur la COPIE stockée :
 * on ne fait confiance qu'au fichier réellement enregistré (pas de changement entre contrôle et usage).
 */
public class FichierService {

    public static final long TAILLE_MAX_OCTETS = 20L * 1024 * 1024;   // 20 Mo
    private static final byte[] SIGNATURE_PDF = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final Path dossierStockage;

    public FichierService() {
        this(Path.of("storage"));
    }

    /** Constructeur utile pour les tests (dossier temporaire). */
    public FichierService(Path dossierStockage) {
        this.dossierStockage = Objects.requireNonNull(dossierStockage);
    }

    public Path getDossierStockage() {
        return dossierStockage;
    }

    /**
     * Contrôle le PDF choisi par l'utilisateur : fichier existant, extension .pdf,
     * taille entre 1 octet et 20 Mo, signature %PDF.
     */
    public void verifierPdf(Path source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Aucun fichier PDF sélectionné.");
        }
        if (!Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new IllegalArgumentException("Le fichier est introuvable ou illisible.");
        }

        String nom = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!nom.endsWith(".pdf")) {
            throw new IllegalArgumentException("Seuls les fichiers .pdf sont acceptés.");
        }

        verifierTailleEtSignature(source);
    }

    /**
     * Copie le PDF dans storage/ sous un nom UUID, puis renseigne dans le mémoire :
     * chemin, nom d'origine, taille et empreinte SHA-256.
     */
    public void stocker(Path source, Memoire memoire) throws IOException {
        verifierPdf(source);

        Files.createDirectories(dossierStockage);
        Path destination = dossierStockage.resolve(UUID.randomUUID() + ".pdf");

        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);

        try {
            // Contrôle refait sur la copie : c'est elle qui sera servie aux lecteurs.
            verifierTailleEtSignature(destination);

            memoire.setCheminFichier(destination.toString());
            memoire.setNomOriginal(nomOriginalNettoye(source));
            memoire.setTailleOctets(Files.size(destination));
            memoire.setEmpreinteSha256(HashUtil.sha256(destination));

        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(destination);
            throw e;
        }
    }

    /** Supprime un PDF stocké. Refuse tout chemin situé hors du dossier de stockage. */
    public void supprimer(String cheminFichier) throws IOException {
        if (cheminFichier == null || cheminFichier.isBlank()) {
            return;
        }

        Path racine = dossierStockage.toAbsolutePath().normalize();
        Path fichier = Path.of(cheminFichier).toAbsolutePath().normalize();

        if (!fichier.startsWith(racine)) {
            throw new SecurityException("Suppression refusée : fichier hors du dossier de stockage.");
        }

        Files.deleteIfExists(fichier);
    }

    private void verifierTailleEtSignature(Path fichier) throws IOException {
        long taille = Files.size(fichier);

        if (taille == 0) {
            throw new IllegalArgumentException("Le fichier PDF est vide.");
        }
        if (taille > TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("Le fichier dépasse la taille maximale de 20 Mo.");
        }

        byte[] debut = new byte[SIGNATURE_PDF.length];
        try (InputStream in = Files.newInputStream(fichier)) {
            if (in.readNBytes(debut, 0, debut.length) != debut.length
                    || !Arrays.equals(debut, SIGNATURE_PDF)) {
                throw new IllegalArgumentException(
                        "Le fichier n'est pas un vrai PDF (signature %PDF absente).");
            }
        }
    }

    /** Garde seulement le nom du fichier (jamais le chemin du poste de l'étudiant), 255 caractères max. */
    private String nomOriginalNettoye(Path source) {
        String nom = source.getFileName().toString().replaceAll("[\\p{Cntrl}]", "");
        return nom.length() > 255 ? nom.substring(nom.length() - 255) : nom;
    }
}
