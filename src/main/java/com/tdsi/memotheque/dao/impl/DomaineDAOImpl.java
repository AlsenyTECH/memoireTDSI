package com.tdsi.memotheque.dao.impl;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.dao.DomaineDAO;
import com.tdsi.memotheque.model.Domaine;

/** [M6] Implémentation JDBC de la gestion des domaines. */
public class DomaineDAOImpl implements DomaineDAO {

    @Override public void insert(Domaine d) throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public void update(Domaine d) throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public void delete(int id) throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public Domaine findById(int id) throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public List<Domaine> findAll() throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
    @Override public boolean estUtilise(int idDomaine) throws SQLException { throw new UnsupportedOperationException("TODO M5"); }
}
