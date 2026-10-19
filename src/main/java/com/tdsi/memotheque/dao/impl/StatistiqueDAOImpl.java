package com.tdsi.memotheque.dao.impl;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.tdsi.memotheque.dao.StatistiqueDAO;
import com.tdsi.memotheque.model.Memoire;

/** [M5] Implémentation JDBC des statistiques du tableau de bord. */
public class StatistiqueDAOImpl implements StatistiqueDAO {

    @Override public Map<String, Integer> memoiresParDomaine() throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public Map<String, Integer> memoiresParAnnee() throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public Map<String, Integer> memoiresParNiveau() throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public Map<String, Integer> memoiresParStatut() throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public List<Memoire> plusConsultes(int limite) throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public int totalMemoiresValides() throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public int totalEtudiants() throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
}
