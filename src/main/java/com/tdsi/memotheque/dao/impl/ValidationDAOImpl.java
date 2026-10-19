package com.tdsi.memotheque.dao.impl;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.dao.ValidationDAO;
import com.tdsi.memotheque.model.Memoire;

/** [M5] Implémentation JDBC de la validation / du refus des mémoires. */
public class ValidationDAOImpl implements ValidationDAO {

    @Override public List<Memoire> findEnAttente(Integer idEncadreur) throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public void valider(int idMemoire, int idValidateur) throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public void refuser(int idMemoire, int idValidateur, String motif) throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
}
