package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;

/** Contrat des comptes utilisateurs. Interface [M1], implémentation [M2]. */
public interface UtilisateurDAO extends GenericDAO<Utilisateur> {
    Utilisateur findByEmailOuMatricule(String identifiant) throws SQLException;
    boolean existeMatricule(String matricule) throws SQLException;
    boolean existeEmail(String email) throws SQLException;
    List<Utilisateur> findByRole(Role role) throws SQLException;        // liste des encadreurs
    void changerActivation(int id, boolean actif) throws SQLException;
    void mettreAJourTentatives(int id, int tentatives, boolean verrouille) throws SQLException;
    void changerMotDePasse(int id, String hash, String sel) throws SQLException;
}
