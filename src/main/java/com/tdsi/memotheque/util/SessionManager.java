package com.tdsi.memotheque.util;

import java.time.Duration;
import java.time.Instant;

import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;

/**
 * [M1] Mémorise l'utilisateur connecté et son rôle (utilisé par les services pour le contrôle d'accès).
 * Retient aussi l'heure de la dernière activité pour la déconnexion automatique (SurveillanceInactivite).
 */
public final class SessionManager {

    private static Utilisateur courant;
    private static volatile Instant derniereActivite = Instant.now();

    private SessionManager() { }

    public static void ouvrir(Utilisateur u) {
        courant = u;
        toucher();
    }

    public static void fermer() { courant = null; }

    public static Utilisateur getUtilisateur() { return courant; }

    public static boolean estConnecte() { return courant != null; }

    public static Role getRole() { return courant == null ? null : courant.getRole(); }

    /** Vrai si l'utilisateur connecté possède l'un des rôles donnés. */
    public static boolean aLeRole(Role... roles) {
        if (courant == null || courant.getRole() == null) {
            return false;
        }
        for (Role r : roles) {
            if (r == courant.getRole()) {
                return true;
            }
        }
        return false;
    }

    /** Appelé à chaque clic ou frappe clavier. */
    public static void toucher() {
        derniereActivite = Instant.now();
    }

    /** Vrai si une session est ouverte et qu'aucune activité n'a eu lieu depuis plus que le délai. */
    public static boolean estInactiveDepuis(Duration delai, Instant maintenant) {
        return courant != null && Duration.between(derniereActivite, maintenant).compareTo(delai) > 0;
    }
}
