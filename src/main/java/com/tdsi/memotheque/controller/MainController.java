package com.tdsi.memotheque.controller;

import com.tdsi.memotheque.util.SceneNavigator;
import com.tdsi.memotheque.util.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;

/**
 * [M1] Cadre principal : menu à gauche, contenu au centre.
 * À faire (M1) : afficher / masquer les boutons du menu selon SessionManager.getRole()
 * (voir le tableau « Navigation et menus par rôle » du document de conception).
 */
public class MainController {

    @FXML
    private StackPane contenu;

    @FXML
    private void initialize() {
        SceneNavigator.definirZoneContenu(contenu);
        SceneNavigator.charger("dashboard");
    }

    @FXML private void allerDashboard()   { SceneNavigator.charger("dashboard"); }
    @FXML private void allerRecherche()   { SceneNavigator.charger("recherche"); }
    @FXML private void allerDepot()       { SceneNavigator.charger("depot"); }
    @FXML private void allerMesMemoires() { SceneNavigator.charger("mes_memoires"); }
    @FXML private void allerValidation()  { SceneNavigator.charger("validation"); }
    @FXML private void allerComptes()     { SceneNavigator.charger("comptes"); }
    @FXML private void allerDomaines()    { SceneNavigator.charger("domaines"); }
    @FXML private void allerJournal()     { SceneNavigator.charger("journal"); }
    @FXML private void allerProfil()      { SceneNavigator.charger("profil"); }

    @FXML
    private void deconnexion() {
        SessionManager.fermer();
        SceneNavigator.afficherLogin();
    }
}
