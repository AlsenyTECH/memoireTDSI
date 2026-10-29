
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
import java.nio.file.Path;


/**
 * M4 : recherche et consultation des mémoires.
 */
public class RechercheService {

    private final MemoireRechercheDAO rechercheDAO;
    private final FichierService fichierService;

    public RechercheService() {
        this(new MemoireRechercheDAOImpl());
    }

    // Constructeur utile pour les tests.
    public RechercheService(MemoireRechercheDAO rechercheDAO) {
        this(rechercheDAO, new FichierService());
    }

    public RechercheService(MemoireRechercheDAO rechercheDAO, FichierService fichierService) {
        this.rechercheDAO = Objects.requireNonNull(rechercheDAO);
        this.fichierService = Objects.requireNonNull(fichierService);
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
     * Vérifie qu'un mémoire est validé, puis déchiffre son PDF dans un fichier temporaire
     * privé après contrôle du tag AES-GCM et du SHA-256. Ne modifie aucun compteur.
     */
    private Path verifierIntegritePdf(Memoire memoire) throws IOException {

        if (memoire == null) {
            throw new IllegalArgumentException("Aucun mémoire sélectionné.");
        }

        if (memoire.getStatut() != StatutMemoire.VALIDE) {
            throw new SecurityException("Ce mémoire n'est pas validé.");
        }

        return fichierService.extraireVersTemporaire(memoire);
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
