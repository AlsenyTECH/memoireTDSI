package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.model.Memoire;

/** Validation / refus des mémoires. Interface [M1], implémentation [M5]. */
public interface ValidationDAO {
    List<Memoire> findEnAttente(Integer idEncadreur) throws SQLException;   // null = tous (admin)
    void valider(int idMemoire, int idValidateur) throws SQLException;
    void refuser(int idMemoire, int idValidateur, String motif) throws SQLException;
}
