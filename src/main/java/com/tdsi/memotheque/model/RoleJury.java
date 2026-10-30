package com.tdsi.memotheque.model;

/** Rôle d'un enseignant dans le jury de soutenance. */
public enum RoleJury {
    PRESIDENT("Président"),
    EXAMINATEUR("Examinateur"),
    ENCADREUR("Encadreur");

    private final String libelle;

    RoleJury(String libelle) { this.libelle = libelle; }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }
}
