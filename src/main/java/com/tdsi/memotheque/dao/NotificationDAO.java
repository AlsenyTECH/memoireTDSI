package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.model.Notification;

/** Notifications internes. */
public interface NotificationDAO {
    void insert(Notification n) throws SQLException;
    /** Notifications de l'utilisateur, non lues d'abord puis les plus récentes. */
    List<Notification> findByUtilisateur(int idUtilisateur, int limite) throws SQLException;
    int compterNonLues(int idUtilisateur) throws SQLException;
    void marquerToutesLues(int idUtilisateur) throws SQLException;
    void supprimerLues(int idUtilisateur) throws SQLException;
}
