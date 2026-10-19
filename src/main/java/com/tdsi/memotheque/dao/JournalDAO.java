package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.model.JournalActivite;

/** Contrat du journal d'audit. Interface [M1], implémentation [M6]. */
public interface JournalDAO {
    void enregistrer(JournalActivite j) throws SQLException;
    List<JournalActivite> derniers(int limite) throws SQLException;
}
