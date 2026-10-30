package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import com.tdsi.memotheque.model.Soutenance;

/** Soutenances et jurys. */
public interface SoutenanceDAO {
    /** Crée ou remplace la soutenance du mémoire et son jury (une seule transaction). */
    void enregistrer(Soutenance s) throws SQLException;
    Soutenance findByMemoire(int idMemoire) throws SQLException;
    /** Toutes les soutenances, par date. */
    List<Soutenance> findAll() throws SQLException;
    /** Soutenances où l'enseignant est membre du jury. */
    List<Soutenance> findByEnseignant(int idEnseignant) throws SQLException;
    void marquerTenue(int idMemoire, String observations) throws SQLException;
    void supprimer(int idMemoire) throws SQLException;
    /**
     * Soutenances (hors idMemoireExclu) qui chevauchent [debut, fin[ et qui utilisent la même salle
     * ou mobilisent l'un des enseignants donnés.
     */
    List<Soutenance> conflits(LocalDateTime debut, LocalDateTime fin, String salle, List<Integer> enseignants,
            int idMemoireExclu) throws SQLException;
}
