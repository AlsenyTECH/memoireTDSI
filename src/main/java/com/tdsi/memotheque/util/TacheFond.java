package com.tdsi.memotheque.util;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.Node;

/**
 * Exécute un traitement long (chiffrement, PBKDF2, signature...) hors du fil JavaFX :
 * l'interface ne fige pas. Les nœuds donnés sont désactivés pendant le traitement ;
 * les rappels succès / échec sont exécutés sur le fil JavaFX.
 */
public final class TacheFond {

    private TacheFond() { }

    public static <T> void executer(Callable<T> traitement, Consumer<T> succes, Consumer<Throwable> echec,
            Node... aDesactiver) {
        Task<T> tache = new Task<>() {
            @Override
            protected T call() throws Exception {
                return traitement.call();
            }
        };
        occuper(true, aDesactiver);
        tache.setOnSucceeded(e -> {
            occuper(false, aDesactiver);
            succes.accept(tache.getValue());
        });
        tache.setOnFailed(e -> {
            occuper(false, aDesactiver);
            echec.accept(tache.getException());
        });
        Thread t = new Thread(tache, "memotdsi-tache");
        t.setDaemon(true);
        t.start();
    }

    /** Message à montrer à l'utilisateur pour une exception (règle métier ou erreur technique). */
    public static String message(Throwable e) {
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException
                || e instanceof SecurityException) {
            return e.getMessage();
        }
        return "Erreur technique : " + e.getMessage();
    }

    private static void occuper(boolean occupe, Node... noeuds) {
        for (Node n : noeuds) {
            n.setDisable(occupe);
            if (n.getScene() != null) {
                n.getScene().setCursor(occupe ? Cursor.WAIT : Cursor.DEFAULT);
            }
        }
    }
}
