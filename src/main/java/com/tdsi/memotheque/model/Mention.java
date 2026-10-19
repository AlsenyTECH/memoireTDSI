package com.tdsi.memotheque.model;

/** Mention obtenue à la soutenance. [M1] contrat gelé */
public enum Mention {
    PASSABLE("Passable"),
    ASSEZ_BIEN("Assez bien"),
    BIEN("Bien"),
    TRES_BIEN("Très bien");

    private final String libelle;

    Mention(String libelle) { this.libelle = libelle; }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }
}
