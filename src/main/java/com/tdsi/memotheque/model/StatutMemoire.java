package com.tdsi.memotheque.model;

/**
 * Cycle de vie d'un mémoire. [M1]
 * EN_ATTENTE (soumis à l'encadreur) -> REFUSE (corrections demandées, l'étudiant corrige et re-soumet)
 *                                   -> AUTORISE (avis favorable : la scolarité planifie la soutenance)
 *                                   -> VALIDE (PV saisi, mémoire publié au catalogue).
 */
public enum StatutMemoire {
    EN_ATTENTE("Soumis à l'encadreur"),
    VALIDE("Publié"),
    REFUSE("Corrections demandées"),
    AUTORISE("Autorisé à soutenir");

    private final String libelle;

    StatutMemoire(String libelle) { this.libelle = libelle; }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }
}
