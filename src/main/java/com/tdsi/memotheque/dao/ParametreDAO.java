package com.tdsi.memotheque.dao;

import java.sql.SQLException;

/** Paramètres clé / valeur de la scolarité. */
public interface ParametreDAO {
    String lire(String cle) throws SQLException;
    void ecrire(String cle, String valeur) throws SQLException;
}
