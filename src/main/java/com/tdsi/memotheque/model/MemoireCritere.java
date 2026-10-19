package com.tdsi.memotheque.model;

import java.time.LocalDate;

/**
 * Filtres de recherche d'un mémoire. [M1] contrat gelé
 * Un critère laissé à null est ignoré par la recherche.
 */
public class MemoireCritere {

    private String motCle;
    private Integer idDomaine;
    private String anneeAcademique;
    private Niveau niveau;
    private Integer idEncadreur;
    private Mention mention;
    private LocalDate dateDebut;           // sur date_publication
    private LocalDate dateFin;             // sur date_publication
    private StatutMemoire statut = StatutMemoire.VALIDE;
    private Integer idAuteur;              // utilisé par « Mes mémoires »
    private TriMemoire tri = TriMemoire.PLUS_RECENTS;
    private int page = 1;
    private int taille = 20;

    public String getMotCle() { return motCle; }
    public void setMotCle(String motCle) { this.motCle = motCle; }
    public Integer getIdDomaine() { return idDomaine; }
    public void setIdDomaine(Integer idDomaine) { this.idDomaine = idDomaine; }
    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }
    public Niveau getNiveau() { return niveau; }
    public void setNiveau(Niveau niveau) { this.niveau = niveau; }
    public Integer getIdEncadreur() { return idEncadreur; }
    public void setIdEncadreur(Integer idEncadreur) { this.idEncadreur = idEncadreur; }
    public Mention getMention() { return mention; }
    public void setMention(Mention mention) { this.mention = mention; }
    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
    public StatutMemoire getStatut() { return statut; }
    public void setStatut(StatutMemoire statut) { this.statut = statut; }
    public Integer getIdAuteur() { return idAuteur; }
    public void setIdAuteur(Integer idAuteur) { this.idAuteur = idAuteur; }
    public TriMemoire getTri() { return tri; }
    public void setTri(TriMemoire tri) { this.tri = tri; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getTaille() { return taille; }
    public void setTaille(int taille) { this.taille = taille; }

    /** Décalage SQL (OFFSET) calculé à partir de la page. */
    public int getOffset() { return (Math.max(page, 1) - 1) * taille; }
}
