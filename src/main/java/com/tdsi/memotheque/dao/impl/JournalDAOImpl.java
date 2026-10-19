package com.tdsi.memotheque.dao.impl;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.dao.JournalDAO;
import com.tdsi.memotheque.model.JournalActivite;

/** [M6] Implémentation JDBC de JournalDAO. */
public class JournalDAOImpl implements JournalDAO {

    @Override public void enregistrer(JournalActivite j) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public List<JournalActivite> derniers(int limite) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
}
