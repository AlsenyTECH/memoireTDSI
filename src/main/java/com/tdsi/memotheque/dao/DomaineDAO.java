package com.tdsi.memotheque.dao;

import java.sql.SQLException;

import com.tdsi.memotheque.model.Domaine;

/** Gestion des domaines. Interface [M1], implémentation [M6]. */
public interface DomaineDAO extends GenericDAO<Domaine> {
    boolean estUtilise(int idDomaine) throws SQLException;   // empêche la suppression si utilisé
}
