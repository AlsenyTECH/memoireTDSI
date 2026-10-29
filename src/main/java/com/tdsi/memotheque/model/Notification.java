package com.tdsi.memotheque.model;

import java.time.LocalDateTime;

/** Message interne destiné à un utilisateur (dépôt à valider, mémoire validé, refus...). */
public class Notification {

    private int id;
    private int idUtilisateur;
    private String message;
    private boolean lue;
    private LocalDateTime dateHeure;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getIdUtilisateur() { return idUtilisateur; }
    public void setIdUtilisateur(int idUtilisateur) { this.idUtilisateur = idUtilisateur; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isLue() { return lue; }
    public void setLue(boolean lue) { this.lue = lue; }
    public LocalDateTime getDateHeure() { return dateHeure; }
    public void setDateHeure(LocalDateTime dateHeure) { this.dateHeure = dateHeure; }
}
