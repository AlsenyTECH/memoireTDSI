package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tdsi.memotheque.dao.StatistiqueDAO;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.util.DatabaseConnection;

/**
 * [M5] Implémentation JDBC des statistiques du tableau de bord.
 * Les répartitions portent sur les mémoires publiés (VALIDE), sauf memoiresParStatut.
 * Les requêtes sont fixes (aucune saisie utilisateur dans le SQL).
 */
public class StatistiqueDAOImpl implements StatistiqueDAO {

    @Override
    public Map<String, Integer> memoiresParDomaine() throws SQLException {
        return compterPar("""
                SELECT d.nom AS cle, COUNT(*) AS nb
                FROM memoire m JOIN domaine d ON d.id = m.id_domaine
                WHERE m.statut = 'VALIDE'
                GROUP BY d.nom
                ORDER BY nb DESC, d.nom
                """);
    }

    @Override
    public Map<String, Integer> memoiresParAnnee() throws SQLException {
        return compterPar("""
                SELECT annee_academique AS cle, COUNT(*) AS nb
                FROM memoire
                WHERE statut = 'VALIDE'
                GROUP BY annee_academique
                ORDER BY annee_academique
                """);
    }

    @Override
    public Map<String, Integer> memoiresParNiveau() throws SQLException {
        return compterPar("""
                SELECT niveau AS cle, COUNT(*) AS nb
                FROM memoire
                WHERE statut = 'VALIDE'
                GROUP BY niveau
                ORDER BY CASE niveau WHEN 'L1' THEN 1 WHEN 'L2' THEN 2 WHEN 'L3' THEN 3
                                     WHEN 'M1' THEN 4 ELSE 5 END
                """);
    }

    @Override
    public Map<String, Integer> memoiresParStatut() throws SQLException {
        return compterPar("""
                SELECT statut AS cle, COUNT(*) AS nb
                FROM memoire
                GROUP BY statut
                ORDER BY CASE statut WHEN 'EN_ATTENTE' THEN 1 WHEN 'VALIDE' THEN 2 ELSE 3 END
                """);
    }

    @Override
    public List<Memoire> plusConsultes(int limite) throws SQLException {
        if (limite < 1) {
            throw new IllegalArgumentException("La limite doit être supérieure à zéro.");
        }

        String sql = MemoireDAOImpl.SELECT_COMPLET
                + " WHERE m.statut = 'VALIDE' ORDER BY m.nb_vues DESC, m.nb_telechargements DESC, m.id"
                + " LIMIT ?";

        List<Memoire> memoires = new ArrayList<>();

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, Math.min(limite, 100));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    memoires.add(MemoireDAOImpl.mapperMemoire(rs));
                }
            }
        }

        return memoires;
    }

    @Override
    public int totalMemoiresValides() throws SQLException {
        return compter("SELECT COUNT(*) FROM memoire WHERE statut = 'VALIDE'");
    }

    @Override
    public int totalEtudiants() throws SQLException {
        return compter("SELECT COUNT(*) FROM utilisateur WHERE role = 'ETUDIANT'");
    }

    private Map<String, Integer> compterPar(String sql) throws SQLException {
        Map<String, Integer> resultat = new LinkedHashMap<>();

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                resultat.put(rs.getString("cle"), rs.getInt("nb"));
            }
        }

        return resultat;
    }

    private int compter(String sql) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
