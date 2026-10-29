package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireVersion;

/** Historique des PDF remplacés d'un mémoire. */
public interface MemoireVersionDAO {
    /** Archive le fichier actuel du mémoire (avant son remplacement). */
    void archiver(Memoire etatActuel) throws SQLException;
    /** Versions archivées, de la plus récente à la plus ancienne. */
    List<MemoireVersion> findByMemoire(int idMemoire) throws SQLException;
    /** Mise à jour du chemin (chiffrement des anciens fichiers en clair). */
    void changerChemin(int idVersion, String chemin) throws SQLException;
    List<MemoireVersion> findAll() throws SQLException;
}
