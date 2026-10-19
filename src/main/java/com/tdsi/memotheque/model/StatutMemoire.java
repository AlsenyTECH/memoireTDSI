package com.tdsi.memotheque.model;

/** Cycle de vie d'un mémoire. [M1] contrat gelé */
public enum StatutMemoire {
    EN_ATTENTE("En attente"),
    VALIDE("Validé"),
    REFUSE("Refusé");

    private final String libelle;

    StatutMemoire(String libelle) { this.libelle = libelle; }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }
}
