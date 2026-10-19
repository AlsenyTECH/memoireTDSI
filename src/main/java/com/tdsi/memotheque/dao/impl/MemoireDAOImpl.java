package com.tdsi.memotheque.dao.impl;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.dao.MemoireDAO;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Niveau;

/** [M3] Implémentation JDBC du CRUD des mémoires. */
public class MemoireDAOImpl implements MemoireDAO {

    @Override public void insert(Memoire m) throws SQLException { throw new UnsupportedOperationException("TODO M3"); }
    @Override public void update(Memoire m) throws SQLException { throw new UnsupportedOperationException("TODO M3"); }
    @Override public void delete(int id) throws SQLException { throw new UnsupportedOperationException("TODO M3"); }
    @Override public Memoire findById(int id) throws SQLException { throw new UnsupportedOperationException("TODO M3"); }
    @Override public List<Memoire> findAll() throws SQLException { throw new UnsupportedOperationException("TODO M3"); }
    @Override public List<Memoire> findByAuteur(int idAuteur) throws SQLException { throw new UnsupportedOperationException("TODO M3"); }
    @Override public boolean existePourAuteurNiveauAnnee(int idAuteur, Niveau n, String annee) throws SQLException { throw new UnsupportedOperationException("TODO M3"); }
}
