package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireCritere;

/** Recherche multicritères. Interface [M1], implémentation [M4]. */
public interface MemoireRechercheDAO {
    List<Memoire> rechercher(MemoireCritere c) throws SQLException;
    int compter(MemoireCritere c) throws SQLException;          // pour la pagination
    List<String> listerAnneesAcademiques() throws SQLException;
    void incrementerVues(int idMemoire) throws SQLException;
    void incrementerTelechargements(int idMemoire) throws SQLException;
}
