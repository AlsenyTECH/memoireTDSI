package com.tdsi.memotheque.dao;

import java.sql.SQLException;

/** Secrets TOTP (double authentification), chiffrés. */
public interface DoubleFacteurDAO {

    /** Secret chiffré et dernier pas accepté. */
    record Enregistrement(int idUtilisateur, String secretChiffre, long dernierPas) { }

    Enregistrement lire(int idUtilisateur) throws SQLException;

    void enregistrer(int idUtilisateur, String secretChiffre) throws SQLException;

    /** Mémorise le pas accepté ; échoue (retourne faux) si un pas égal ou plus récent a déjà servi. */
    boolean consommerPas(int idUtilisateur, long pas) throws SQLException;

    void supprimer(int idUtilisateur) throws SQLException;
}
