package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.tdsi.memotheque.dao.NotificationDAO;
import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.dao.impl.NotificationDAOImpl;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Notification;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.SessionManager;

/**
 * Notifications internes. L'envoi ne fait jamais échouer l'action principale
 * (un dépôt réussi reste réussi même si la notification ne part pas).
 * Chaque utilisateur ne lit que SES notifications.
 */
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class.getName());
    private static final int MESSAGE_MAX = 500;

    private final NotificationDAO notificationDAO;
    private final UtilisateurDAO utilisateurDAO;

    public NotificationService() {
        this(new NotificationDAOImpl(), new UtilisateurDAOImpl());
    }

    public NotificationService(NotificationDAO notificationDAO, UtilisateurDAO utilisateurDAO) {
        this.notificationDAO = Objects.requireNonNull(notificationDAO);
        this.utilisateurDAO = Objects.requireNonNull(utilisateurDAO);
    }

    public void notifier(int idUtilisateur, String message) {
        try {
            Notification n = new Notification();
            n.setIdUtilisateur(idUtilisateur);
            n.setMessage(message.length() > MESSAGE_MAX ? message.substring(0, MESSAGE_MAX) + "…" : message);
            notificationDAO.insert(n);
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.WARNING, "Notification non envoyée à l'utilisateur #" + idUtilisateur, e);
        }
    }

    /** À tous les administrateurs actifs (nouvelle inscription à activer...). */
    public void notifierAdmins(String message) {
        try {
            for (Utilisateur admin : utilisateurDAO.findByRole(Role.ADMIN)) {
                if (admin.isActif()) {
                    notifier(admin.getId(), message);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Notification des administrateurs impossible", e);
        }
    }

    public List<Notification> mesNotifications() throws SQLException {
        return notificationDAO.findByUtilisateur(exigerConnexion().getId(), 200);
    }

    public int compterNonLues() throws SQLException {
        Utilisateur u = SessionManager.getUtilisateur();
        return u == null ? 0 : notificationDAO.compterNonLues(u.getId());
    }

    public void marquerToutesLues() throws SQLException {
        notificationDAO.marquerToutesLues(exigerConnexion().getId());
    }

    public void supprimerLues() throws SQLException {
        notificationDAO.supprimerLues(exigerConnexion().getId());
    }

    private Utilisateur exigerConnexion() {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null) {
            throw new SecurityException("Vous devez être connecté.");
        }
        return u;
    }
}
