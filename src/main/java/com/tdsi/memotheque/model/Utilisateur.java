package com.tdsi.memotheque.model;

import java.time.LocalDate;

/** Étudiant (« tdsien »), encadreur ou administrateur. [M1] contrat gelé */
public class Utilisateur {

    private int id;
    private String matricule;          // null pour enseignants / admin
    private String nom;
    private String prenom;
    private String email;
    private String motDePasseHash;
    private String sel;
    private Role role;
    private Niveau niveau;             // étudiants seulement
    private boolean actif;
    private int tentativesEchec;
    private boolean verrouille;
    private LocalDate dateCreation;

    public Utilisateur() { }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMotDePasseHash() { return motDePasseHash; }
    public void setMotDePasseHash(String motDePasseHash) { this.motDePasseHash = motDePasseHash; }
    public String getSel() { return sel; }
    public void setSel(String sel) { this.sel = sel; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Niveau getNiveau() { return niveau; }
    public void setNiveau(Niveau niveau) { this.niveau = niveau; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public int getTentativesEchec() { return tentativesEchec; }
    public void setTentativesEchec(int tentativesEchec) { this.tentativesEchec = tentativesEchec; }
    public boolean isVerrouille() { return verrouille; }
    public void setVerrouille(boolean verrouille) { this.verrouille = verrouille; }
    public LocalDate getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }

    public String getNomComplet() { return prenom + " " + nom; }

    /** Utilisé par les ComboBox (liste des encadreurs). */
    @Override
    public String toString() { return getNomComplet(); }
}
