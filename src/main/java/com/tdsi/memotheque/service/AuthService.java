package com.tdsi.memotheque.service;

/**
 * [M2] Connexion, inscription « tdsien » et verrouillage de compte.
 * À faire :
 *  - inscrire(...)  : valider matricule/email, email unique, compte créé INACTIF
 *  - connecter(...) : refuser si inactif ou verrouillé, verrouiller après 5 échecs, remettre le compteur à 0 si succès,
 *                     ouvrir la session (SessionManager.ouvrir), journaliser CONNEXION / ECHEC_CONNEXION
 *  - initialiserAdminParDefaut() : crée le compte admin au premier lancement (mot de passe haché avec PasswordUtil)
 */
public class AuthService {
}
