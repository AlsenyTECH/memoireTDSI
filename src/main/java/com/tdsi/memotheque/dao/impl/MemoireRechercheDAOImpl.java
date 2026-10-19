package com.tdsi.memotheque.dao.impl;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.dao.MemoireRechercheDAO;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireCritere;

/**
 * [M4] Implémentation JDBC de la recherche multicritères.
 * Requête construite dynamiquement MAIS toutes les valeurs passent par des « ? ».
 * Le ORDER BY vient de TriMemoire.getClauseSql() (liste fermée).
 */
public class MemoireRechercheDAOImpl implements MemoireRechercheDAO {

    @Override public List<Memoire> rechercher(MemoireCritere c) throws SQLException { throw new UnsupportedOperationException("TODO M4"); }
    @Override public int compter(MemoireCritere c) throws SQLException { throw new UnsupportedOperationException("TODO M4"); }
    @Override public List<String> listerAnneesAcademiques() throws SQLException { throw new UnsupportedOperationException("TODO M4"); }
    @Override public void incrementerVues(int idMemoire) throws SQLException { throw new UnsupportedOperationException("TODO M4"); }
    @Override public void incrementerTelechargements(int idMemoire) throws SQLException { throw new UnsupportedOperationException("TODO M4"); }
}
