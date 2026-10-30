package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.model.Memoire;

/** Validation / refus des mémoires. Interface [M1], implémentation [M5]. */
public interface ValidationDAO {
    List<Memoire> findEnAttente(Integer idEncadreur) throws SQLException;   // null = tous (admin)
    void valider(int idMemoire, int idValidateur) throws SQLException;
    /** Avis favorable de l'encadreur : EN_ATTENTE -> AUTORISE (autorisé à soutenir). */
    void autoriser(int idMemoire, java.time.LocalDate dateAvis) throws SQLException;
    /** PV de soutenance : AUTORISE -> VALIDE (publié), résultats et signature en une seule mise à jour. */
    void publier(com.tdsi.memotheque.model.Memoire m, String signature, int idCle) throws SQLException;
    /** Ajournement en soutenance : AUTORISE -> REFUSE avec motif. */
    void ajourner(int idMemoire, int idValidateur, String motif, Double note) throws SQLException;
    /** Validation et signature en une seule mise à jour (atomique). */
    void validerEtSigner(int idMemoire, int idValidateur, java.time.LocalDate datePublication,
            String signature, int idCle) throws SQLException;
    void refuser(int idMemoire, int idValidateur, String motif) throws SQLException;
}
