package com.tdsi.memotheque.model;

/** Profils d'utilisateurs de la plateforme. [M1] contrat gelé */
public enum Role {
    ETUDIANT("Étudiant"),
    ENCADREUR("Encadreur"),
    ADMIN("Administrateur");

    private final String libelle;

    Role(String libelle) { this.libelle = libelle; }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }
}
