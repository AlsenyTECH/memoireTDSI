package com.tdsi.memotheque.model;

import java.time.LocalDateTime;

/** Une ligne du journal d'audit. [M1] contrat gelé */
public class JournalActivite {

    private int id;
    private Integer idUtilisateur;     // null si l'utilisateur est inconnu (échec de connexion)
    private String action;             // CONNEXION, ECHEC_CONNEXION, DEPOT, VALIDATION...
    private String details;
    private LocalDateTime dateHeure;

    public JournalActivite() { }

    public JournalActivite(Integer idUtilisateur, String action, String details) {
        this.idUtilisateur = idUtilisateur;
        this.action = action;
        this.details = details;
        this.dateHeure = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public Integer getIdUtilisateur() { return idUtilisateur; }
    public void setIdUtilisateur(Integer idUtilisateur) { this.idUtilisateur = idUtilisateur; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getDateHeure() { return dateHeure; }
    public void setDateHeure(LocalDateTime dateHeure) { this.dateHeure = dateHeure; }
}
