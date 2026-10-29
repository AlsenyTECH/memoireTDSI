package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.tdsi.memotheque.dao.StatistiqueDAO;
import com.tdsi.memotheque.dao.impl.StatistiqueDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.util.SessionManager;

/**
 * [M5] Statistiques du tableau de bord.
 * À faire : exposer les données de StatistiqueDAO pour les PieChart / BarChart et les cartes de synthèse.
 * Accessible à tout utilisateur connecté (données agrégées uniquement).
 */
public class StatistiqueService {

    public static final int TOP_CONSULTES = 5;

    private final StatistiqueDAO statistiqueDAO;

    public StatistiqueService() {
        this(new StatistiqueDAOImpl());
    }

    // Constructeur utile pour les tests.
    public StatistiqueService(StatistiqueDAO statistiqueDAO) {
        this.statistiqueDAO = Objects.requireNonNull(statistiqueDAO);
    }

    public int totalMemoiresValides() throws SQLException {
        exigerConnexion();
        return statistiqueDAO.totalMemoiresValides();
    }

    public int totalEtudiants() throws SQLException {
        exigerConnexion();
        return statistiqueDAO.totalEtudiants();
    }

    public int totalEnAttente() throws SQLException {
        exigerConnexion();
        return statistiqueDAO.memoiresParStatut().getOrDefault(StatutMemoire.EN_ATTENTE.name(), 0);
    }

    /** Pour le PieChart. */
    public Map<String, Integer> memoiresParDomaine() throws SQLException {
        exigerConnexion();
        return statistiqueDAO.memoiresParDomaine();
    }

    /** Pour le BarChart. */
    public Map<String, Integer> memoiresParAnnee() throws SQLException {
        exigerConnexion();
        return statistiqueDAO.memoiresParAnnee();
    }

    public Map<String, Integer> memoiresParNiveau() throws SQLException {
        exigerConnexion();
        return statistiqueDAO.memoiresParNiveau();
    }

    /** Répartition par statut avec les libellés lisibles (« En attente », « Validé », « Refusé »). */
    public Map<String, Integer> memoiresParStatut() throws SQLException {
        exigerConnexion();
        Map<String, Integer> libelles = new LinkedHashMap<>();
        statistiqueDAO.memoiresParStatut().forEach(
                (statut, nb) -> libelles.put(StatutMemoire.valueOf(statut).getLibelle(), nb));
        return libelles;
    }

    public List<Memoire> plusConsultes() throws SQLException {
        exigerConnexion();
        return statistiqueDAO.plusConsultes(TOP_CONSULTES);
    }

    private void exigerConnexion() {
        if (!SessionManager.estConnecte()) {
            throw new SecurityException("Vous devez être connecté.");
        }
    }
}
