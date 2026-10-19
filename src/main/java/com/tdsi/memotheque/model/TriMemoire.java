package com.tdsi.memotheque.model;

/**
 * Critères de tri de la recherche. [M1] contrat gelé
 * La clause SQL vient de cette liste FERMÉE : elle n'est jamais saisie par l'utilisateur
 * (protection contre l'injection SQL dans le ORDER BY).
 */
public enum TriMemoire {
    PLUS_RECENTS("Plus récents", "m.date_publication DESC, m.id DESC"),
    PLUS_CONSULTES("Plus consultés", "m.nb_vues DESC, m.id DESC"),
    TITRE_AZ("Titre A → Z", "m.titre COLLATE NOCASE ASC");

    private final String libelle;
    private final String clauseSql;

    TriMemoire(String libelle, String clauseSql) {
        this.libelle = libelle;
        this.clauseSql = clauseSql;
    }

    public String getLibelle() { return libelle; }

    public String getClauseSql() { return clauseSql; }

    @Override
    public String toString() { return libelle; }
}
