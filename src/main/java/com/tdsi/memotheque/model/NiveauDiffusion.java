package com.tdsi.memotheque.model;

/**
 * Autorisation de diffusion donnée par l'étudiant au dépôt (comme sur DUMAS / theses.fr).
 * L'administration peut l'ajuster lors de la publication.
 */
public enum NiveauDiffusion {
    PUBLIC("Diffusion libre", "Notice et PDF consultables par tous les utilisateurs de la plateforme."),
    RESTREINT("Diffusion restreinte", "Notice visible ; PDF réservé aux enseignants et à l'administration."),
    CONFIDENTIEL("Confidentiel (embargo)", "Invisible au catalogue jusqu'à la fin de l'embargo, puis diffusion libre.");

    private final String libelle;
    private final String description;

    NiveauDiffusion(String libelle, String description) {
        this.libelle = libelle;
        this.description = description;
    }

    public String getLibelle() { return libelle; }

    public String getDescription() { return description; }

    @Override
    public String toString() { return libelle; }
}
