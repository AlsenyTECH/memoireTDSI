package com.tdsi.memotheque.model;

/** Domaine de recherche d'un mémoire (Cybersécurité, Réseaux...). [M1] contrat gelé */
public class Domaine {

    private int id;
    private String nom;
    private String description;

    public Domaine() { }

    public Domaine(int id, String nom, String description) {
        this.id = id;
        this.nom = nom;
        this.description = description;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** Utilisé par les ComboBox pour afficher le domaine. */
    @Override
    public String toString() { return nom; }
}
