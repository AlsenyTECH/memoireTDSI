package com.tdsi.memotheque.dao;

import java.sql.SQLException;

import com.tdsi.memotheque.model.CleSignature;

/** Clés de signature des validateurs. */
public interface CleSignatureDAO {
    void insert(CleSignature cle) throws SQLException;
    CleSignature findById(int id) throws SQLException;
    /** Clé active de l'utilisateur, ou null. */
    CleSignature findActive(int idUtilisateur) throws SQLException;
    /** Nouveau chiffrement de la clé privée (changement de mot de passe). */
    void changerChiffrement(int id, String clePriveeChiffree, String sel, int iterations) throws SQLException;
    /** Désactive toutes les clés de l'utilisateur (les clés publiques restent pour la vérification). */
    void desactiver(int idUtilisateur) throws SQLException;
}
