package com.tdsi.memotheque.dao;

import java.sql.SQLException;

import com.tdsi.memotheque.model.DocumentEmis;

/** Registre des documents officiels émis. */
public interface DocumentEmisDAO {

    void insert(DocumentEmis d) throws SQLException;

    DocumentEmis findByCode(String code) throws SQLException;

    /** Dernier document émis pour ce type, ce mémoire, ce destinataire et cet état, ou null. */
    DocumentEmis trouver(String type, int idReference, String sujet, String empreinteEtat) throws SQLException;
}
