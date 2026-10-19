package com.tdsi.memotheque.controller;

import com.tdsi.memotheque.util.SceneNavigator;

import javafx.fxml.FXML;

/**
 * [M2] Écran de connexion.
 * À faire : lire les champs login / mot de passe, appeler AuthService.connecter(...),
 * afficher le message d'erreur, puis SceneNavigator.afficherCadrePrincipal() si succès.
 */
public class LoginController {

    /** TEMPORAIRE (mode développement) : à remplacer par l'appel à AuthService. */
    @FXML
    private void entrerModeDemo() {
        SceneNavigator.afficherCadrePrincipal();
    }
}
