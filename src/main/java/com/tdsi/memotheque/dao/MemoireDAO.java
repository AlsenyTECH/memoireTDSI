package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Niveau;

/** CRUD des mémoires. Interface [M1], implémentation [M3]. */
public interface MemoireDAO extends GenericDAO<Memoire> {
    List<Memoire> findByAuteur(int idAuteur) throws SQLException;
    boolean existePourAuteurNiveauAnnee(int idAuteur, Niveau n, String annee) throws SQLException;
}
