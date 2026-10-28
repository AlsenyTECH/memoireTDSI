
package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import com.tdsi.memotheque.dao.MemoireRechercheDAO;
import com.tdsi.memotheque.dao.impl.MemoireRechercheDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireCritere;
import com.tdsi.memotheque.model.StatutMemoire;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import com.tdsi.memotheque.util.HashUtil;

/**
 * M4 : recherche et consultation des mémoires.
 */
public class RechercheService {

    private final MemoireRechercheDAO rechercheDAO;

    public RechercheService() {
        this(new MemoireRechercheDAOImpl());
    }

    // Constructeur utile pour les tests.
    public RechercheService(MemoireRechercheDAO rechercheDAO) {
        this.rechercheDAO = Objects.requireNonNull(rechercheDAO);
    }

    /**
     * Recherche publique : seuls les mémoires VALIDE sont visibles.
     */
    public List<Memoire> rechercherPublic(MemoireCritere critere)
            throws SQLException {

        MemoireCritere criteres = critere == null
                ? new MemoireCritere()
                : critere;

        criteres.setStatut(StatutMemoire.VALIDE);

        return rechercheDAO.rechercher(criteres);
    }

    /**
     * Compte uniquement les mémoires accessibles publiquement.
     */
    public int compterPublic(MemoireCritere critere)
            throws SQLException {

        MemoireCritere criteres = critere == null
                ? new MemoireCritere()
                : critere;

        criteres.setStatut(StatutMemoire.VALIDE);

        return rechercheDAO.compter(criteres);
    }

    /**
     * Liste les années académiques des mémoires validés.
     */
    public List<String> listerAnneesAcademiques()
            throws SQLException {

        return rechercheDAO.listerAnneesAcademiques();
    }
    
    
    /**
     * Vérifie qu'un mémoire est validé et que son PDF est intègre.
     * Ne modifie aucun compteur.
     */
    private Path verifierIntegritePdf(Memoire memoire) throws IOException {

        if (memoire == null) {
            throw new IllegalArgumentException("Aucun mémoire sélectionné.");
        }

        if (memoire.getStatut() != StatutMemoire.VALIDE) {
            throw new SecurityException("Ce mémoire n'est pas validé.");
        }

        if (memoire.getCheminFichier() == null
                || memoire.getCheminFichier().isBlank()) {
            throw new IOException("Le chemin du PDF est absent.");
        }

        Path fichier = Path.of(memoire.getCheminFichier())
                .toAbsolutePath()
                .normalize();

        if (!Files.isRegularFile(fichier) || !Files.isReadable(fichier)) {
            throw new IOException("Le fichier PDF est absent ou illisible.");
        }

        String empreinteEnregistree = memoire.getEmpreinteSha256();
        String empreinteActuelle = HashUtil.sha256(fichier);

        if (empreinteEnregistree == null
                || empreinteEnregistree.isBlank()
                || !MessageDigest.isEqual(
                        empreinteEnregistree.trim()
                                .toLowerCase(java.util.Locale.ROOT)
                                .getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        empreinteActuelle.getBytes(
                                java.nio.charset.StandardCharsets.US_ASCII))) {

            throw new SecurityException(
                    "L'intégrité du PDF n'est pas vérifiée. "
                    + "Le fichier a peut-être été modifié."
            );
        }

        return fichier;
    }
    
    /**
     * Vérifie l'intégrité d'un mémoire avant sa consultation.
     * Retourne le chemin du PDF si toutes les vérifications réussissent.
     */
    /**
     * Vérifie le PDF avant consultation et incrémente le compteur de vues.
     */
    public Path preparerConsultation(Memoire memoire)
            throws SQLException, IOException {

        Path fichier = verifierIntegritePdf(memoire);

        rechercheDAO.incrementerVues(memoire.getId());

        return fichier;
    }
    
    /**
     * Vérifie le PDF avant téléchargement et incrémente le compteur associé.
     */
    public Path preparerTelechargement(Memoire memoire)
            throws SQLException, IOException {

        Path fichier = verifierIntegritePdf(memoire);

        rechercheDAO.incrementerTelechargements(memoire.getId());

        return fichier;
    }
    
    public void enregistrerTelechargement(int idMemoire) throws SQLException {
        rechercheDAO.incrementerTelechargements(idMemoire);
    }
    
    public Path verifierPdfPourTelechargement(Memoire memoire)
            throws IOException {
        return verifierIntegritePdf(memoire);
    }
}
