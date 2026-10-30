package com.tdsi.memotheque.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Soutenance d'un mémoire : planifiée par la scolarité, puis tenue (PV saisi). */
public class Soutenance {

    public enum Statut {
        PLANIFIEE("Planifiée"), TENUE("Tenue");

        private final String libelle;

        Statut(String libelle) { this.libelle = libelle; }

        @Override
        public String toString() { return libelle; }
    }

    /** Durée réservée pour une soutenance (salle et jury), en minutes. */
    public static final int DUREE_MINUTES = 60;

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm");

    private Memoire memoire;               // titre, auteur, encadreur... pour l'affichage
    private LocalDateTime dateHeure;
    private String salle;
    private Statut statut = Statut.PLANIFIEE;
    private String observations;
    private Integer idPlanificateur;
    private LocalDateTime datePlanification;
    private List<MembreJury> jury = new ArrayList<>();

    public Memoire getMemoire() { return memoire; }
    public void setMemoire(Memoire memoire) { this.memoire = memoire; }
    public int getIdMemoire() { return memoire == null ? 0 : memoire.getId(); }
    public LocalDateTime getDateHeure() { return dateHeure; }
    public void setDateHeure(LocalDateTime dateHeure) { this.dateHeure = dateHeure; }
    public String getSalle() { return salle; }
    public void setSalle(String salle) { this.salle = salle; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }
    public Integer getIdPlanificateur() { return idPlanificateur; }
    public void setIdPlanificateur(Integer idPlanificateur) { this.idPlanificateur = idPlanificateur; }
    public LocalDateTime getDatePlanification() { return datePlanification; }
    public void setDatePlanification(LocalDateTime datePlanification) { this.datePlanification = datePlanification; }
    public List<MembreJury> getJury() { return jury; }
    public void setJury(List<MembreJury> jury) { this.jury = jury; }

    // ---- Aides pour les TableView ----

    public String getDateHeureLisible() { return dateHeure == null ? "" : dateHeure.format(FORMAT); }

    public String getTitre() { return memoire == null ? "" : memoire.getTitre(); }

    public String getEtudiant() { return memoire == null ? "" : memoire.getAuteurNomComplet(); }

    public String getNiveauAnnee() {
        return memoire == null ? "" : memoire.getNiveau() + " " + memoire.getAnneeAcademique();
    }

    public String getJuryLisible() {
        return String.join(" ; ", jury.stream().map(MembreJury::toString).toList());
    }

    /** Rôle d'un enseignant dans ce jury, ou null. */
    public RoleJury roleDe(int idEnseignant) {
        return jury.stream().filter(j -> j.enseignant() != null && j.enseignant().getId() == idEnseignant)
                .map(MembreJury::role).findFirst().orElse(null);
    }
}
