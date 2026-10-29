package com.tdsi.memotheque.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.util.CryptoUtil;
import com.tdsi.memotheque.util.GestionCles;

/**
 * [M3] Contrôle et stockage des PDF.
 * - extension .pdf, taille <= 20 Mo, signature « %PDF » dans les premiers octets ;
 * - nom de stockage UUID (jamais le nom d'origine), empreinte SHA-256 du PDF ;
 * - chiffrement au repos AES-256-GCM (clé du coffre, nom du fichier en données associées) :
 *   une copie de storage/ seule ne permet pas de lire les mémoires, et toute modification
 *   du fichier chiffré est détectée par le tag GCM.
 *
 * Le PDF source est lu UNE seule fois en mémoire : contrôles, hash et chiffrement portent sur
 * les mêmes octets (aucune fenêtre entre le contrôle et l'usage).
 */
public class FichierService {

    public static final long TAILLE_MAX_OCTETS = 20L * 1024 * 1024;   // 20 Mo
    public static final String EXTENSION_CHIFFREE = ".pdf.enc";
    private static final byte[] SIGNATURE_PDF = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private static Path dossierParDefaut = com.tdsi.memotheque.util.Donnees.chemin("storage");
    private static Path dossierTemporaire;

    private final Path dossierStockage;

    /** PDF contrôlé, prêt à être stocké. */
    public record FichierPrepare(byte[] contenu, String sha256, String nomOriginal) { }

    public FichierService() {
        this(dossierParDefaut);
    }

    /** Constructeur utile pour les tests (dossier temporaire). */
    public FichierService(Path dossierStockage) {
        this.dossierStockage = Objects.requireNonNull(dossierStockage);
    }

    /** Les tests placent le stockage par défaut dans leur dossier temporaire. */
    public static synchronized void utiliserDossierParDefaut(Path dossier) {
        dossierParDefaut = dossier;
    }

    public static synchronized Path dossierParDefaut() {
        return dossierParDefaut;
    }

    public Path getDossierStockage() {
        return dossierStockage;
    }

    // ------------------------------------------------------------------ dépôt

    /** Lit et contrôle le PDF choisi par l'utilisateur (une seule lecture). */
    public FichierPrepare lire(Path source) throws IOException {
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
        if (Files.size(source) > TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("Le fichier dépasse la taille maximale de 20 Mo.");
        }

        byte[] contenu;
        try (InputStream in = Files.newInputStream(source)) {
            // Lecture bornée : même si le fichier grossit entre-temps, on ne dépasse pas 20 Mo + 1.
            contenu = in.readNBytes((int) TAILLE_MAX_OCTETS + 1);
        }
        verifierContenu(contenu);

        return new FichierPrepare(contenu, CryptoUtil.sha256Hex(contenu), nomOriginalNettoye(source));
    }

    /** Contrôle seul (extension, taille, signature). */
    public void verifierPdf(Path source) throws IOException {
        lire(source);
    }

    /** Lit, contrôle, chiffre et stocke ; renseigne chemin, nom d'origine, taille et SHA-256. */
    public void stocker(Path source, Memoire memoire) throws IOException {
        stocker(lire(source), memoire);
    }

    public void stocker(FichierPrepare fichier, Memoire memoire) throws IOException {
        Files.createDirectories(dossierStockage);
        String nom = UUID.randomUUID() + EXTENSION_CHIFFREE;
        Path destination = dossierStockage.resolve(nom);

        byte[] chiffre = CryptoUtil.chiffrer(GestionCles.cleChiffrementFichiers(), fichier.contenu(),
                nom.getBytes(StandardCharsets.UTF_8));

        Path temporaire = Files.createTempFile(dossierStockage, "depot", ".tmp");
        try {
            Files.write(temporaire, chiffre);
            Files.move(temporaire, destination, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporaire);
        }

        memoire.setCheminFichier(destination.toString());
        memoire.setNomOriginal(fichier.nomOriginal());
        memoire.setTailleOctets(fichier.contenu().length);
        memoire.setEmpreinteSha256(fichier.sha256());
    }

    // ------------------------------------------------------------------ lecture

    /**
     * Retourne le PDF en clair après déchiffrement (tag GCM) ET contrôle du SHA-256 enregistré.
     * SecurityException si le fichier a été modifié, remplacé ou déplacé hors de storage/.
     */
    public byte[] lireContenuVerifie(Memoire memoire) throws IOException {
        return lireContenuVerifie(memoire.getCheminFichier(), memoire.getEmpreinteSha256());
    }

    public byte[] lireContenuVerifie(String chemin, String empreinteAttendue) throws IOException {
        Path fichier = confiner(chemin);

        if (!Files.isRegularFile(fichier) || !Files.isReadable(fichier)) {
            throw new IOException("Le fichier PDF est absent ou illisible.");
        }

        byte[] octets = Files.readAllBytes(fichier);
        String nom = fichier.getFileName().toString();
        byte[] clair = nom.endsWith(EXTENSION_CHIFFREE)
                ? CryptoUtil.dechiffrer(GestionCles.cleChiffrementFichiers(), octets,
                        nom.getBytes(StandardCharsets.UTF_8))
                : octets;   // ancien dépôt non chiffré (sera chiffré au prochain démarrage)

        String attendue = empreinteAttendue == null ? "" : empreinteAttendue.trim().toLowerCase(Locale.ROOT);
        if (!CryptoUtil.egal(attendue, CryptoUtil.sha256Hex(clair))) {
            throw new SecurityException("L'intégrité du PDF n'est pas vérifiée : le fichier a été modifié.");
        }
        return clair;
    }

    /**
     * Copie en clair dans un dossier temporaire privé (droits 700), pour l'ouvrir avec le lecteur PDF.
     * Le fichier est supprimé à la fermeture de l'application.
     */
    public Path extraireVersTemporaire(Memoire memoire) throws IOException {
        byte[] clair = lireContenuVerifie(memoire);

        String nom = "memoire-" + memoire.getId() + "-"
                + (memoire.getNomOriginal() == null ? "document.pdf"
                        : memoire.getNomOriginal().replaceAll("[^\\p{L}\\p{N}._ -]", "_"));
        if (!nom.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            nom += ".pdf";
        }
        Path cible = dossierTemporaire().resolve(nom);
        Files.write(cible, clair);
        cible.toFile().deleteOnExit();
        return cible;
    }

    /** Écrit le PDF en clair (vérifié) vers une destination choisie par l'utilisateur. */
    public void exporter(Memoire memoire, Path destination) throws IOException {
        Files.write(destination, lireContenuVerifie(memoire));
    }

    /** Vrai si le fichier stocké est encore en clair (dépôt antérieur au chiffrement). */
    public static boolean estEnClair(String chemin) {
        return chemin != null && !chemin.endsWith(EXTENSION_CHIFFREE);
    }

    // ------------------------------------------------------------------ suppression

    /** Supprime un PDF stocké. Refuse tout chemin situé hors du dossier de stockage. */
    public void supprimer(String cheminFichier) throws IOException {
        if (cheminFichier == null || cheminFichier.isBlank()) {
            return;
        }
        Files.deleteIfExists(confiner(cheminFichier));
    }

    // ------------------------------------------------------------------ outils

    /** Le chemin enregistré en base doit rester dans storage/ (base modifiée = chemin non fiable). */
    private Path confiner(String chemin) {
        if (chemin == null || chemin.isBlank()) {
            throw new SecurityException("Chemin du PDF absent.");
        }
        Path racine = dossierStockage.toAbsolutePath().normalize();
        Path fichier = Path.of(chemin).toAbsolutePath().normalize();
        if (!fichier.startsWith(racine)) {
            throw new SecurityException("Accès refusé : fichier hors du dossier de stockage.");
        }
        return fichier;
    }

    private void verifierContenu(byte[] contenu) {
        if (contenu.length == 0) {
            throw new IllegalArgumentException("Le fichier PDF est vide.");
        }
        if (contenu.length > TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("Le fichier dépasse la taille maximale de 20 Mo.");
        }
        if (contenu.length < SIGNATURE_PDF.length
                || !Arrays.equals(contenu, 0, SIGNATURE_PDF.length, SIGNATURE_PDF, 0, SIGNATURE_PDF.length)) {
            throw new IllegalArgumentException("Le fichier n'est pas un vrai PDF (signature %PDF absente).");
        }
    }

    /** Garde seulement le nom du fichier (jamais le chemin du poste de l'étudiant), 255 caractères max. */
    private String nomOriginalNettoye(Path source) {
        String nom = source.getFileName().toString().replaceAll("[\\p{Cntrl}]", "");
        return nom.length() > 255 ? nom.substring(nom.length() - 255) : nom;
    }

    private static synchronized Path dossierTemporaire() throws IOException {
        if (dossierTemporaire == null || !Files.isDirectory(dossierTemporaire)) {
            try {
                dossierTemporaire = Files.createTempDirectory("memotdsi-",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            } catch (UnsupportedOperationException e) {
                dossierTemporaire = Files.createTempDirectory("memotdsi-");   // Windows
            }
            dossierTemporaire.toFile().deleteOnExit();
        }
        return dossierTemporaire;
    }
}
