package com.tdsi.memotheque.util;

import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;

/** [M1] Mémorise l'utilisateur connecté et son rôle (utilisé par les services pour le contrôle d'accès). */
public final class SessionManager {

    private static Utilisateur courant;

    private SessionManager() { }

    public static void ouvrir(Utilisateur u) { courant = u; }

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
}
