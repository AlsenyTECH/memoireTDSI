package com.tdsi.memotheque.dao.impl;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;

/**
 * [M2] Implémentation JDBC de UtilisateurDAO.
 * RAPPEL : PreparedStatement uniquement, jamais de concaténation de saisie dans le SQL.
 */
public class UtilisateurDAOImpl implements UtilisateurDAO {

    @Override public void insert(Utilisateur u) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public void update(Utilisateur u) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public void delete(int id) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public Utilisateur findById(int id) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public List<Utilisateur> findAll() throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public Utilisateur findByEmailOuMatricule(String identifiant) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public boolean existeMatricule(String matricule) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public boolean existeEmail(String email) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public List<Utilisateur> findByRole(Role role) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public void changerActivation(int id, boolean actif) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public void mettreAJourTentatives(int id, int tentatives, boolean verrouille) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
    @Override public void changerMotDePasse(int id, String hash, String sel) throws SQLException { throw new UnsupportedOperationException("TODO M2"); }
}
