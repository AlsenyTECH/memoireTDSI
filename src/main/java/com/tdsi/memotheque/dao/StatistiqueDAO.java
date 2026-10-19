package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.tdsi.memotheque.model.Memoire;

/** Statistiques du tableau de bord. Interface [M1], implémentation [M5]. */
public interface StatistiqueDAO {
    Map<String, Integer> memoiresParDomaine() throws SQLException;
    Map<String, Integer> memoiresParAnnee() throws SQLException;
    Map<String, Integer> memoiresParNiveau() throws SQLException;
    Map<String, Integer> memoiresParStatut() throws SQLException;
    List<Memoire> plusConsultes(int limite) throws SQLException;
    int totalMemoiresValides() throws SQLException;
    int totalEtudiants() throws SQLException;
}
