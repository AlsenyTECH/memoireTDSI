package com.tdsi.memotheque.model;

/** Un enseignant membre du jury d'une soutenance. */
public record MembreJury(Utilisateur enseignant, RoleJury role) {

    @Override
    public String toString() {
        return role.getLibelle() + " : " + (enseignant == null ? "?" : enseignant.getNomComplet());
    }
}
