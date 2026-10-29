package com.tdsi.memotheque.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Ancienne version d'un PDF, conservée quand l'étudiant le remplace (après un refus par exemple). */
public class MemoireVersion {

    private int id;
    private int idMemoire;
    private String cheminFichier;
    private String nomOriginal;
    private long tailleOctets;
    private String empreinteSha256;
    private LocalDate dateDepot;
    private StatutMemoire statut;          // statut du mémoire au moment du remplacement
    private String motifRefus;
    private LocalDateTime dateArchivage;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getIdMemoire() { return idMemoire; }
    public void setIdMemoire(int idMemoire) { this.idMemoire = idMemoire; }
    public String getCheminFichier() { return cheminFichier; }
    public void setCheminFichier(String cheminFichier) { this.cheminFichier = cheminFichier; }
    public String getNomOriginal() { return nomOriginal; }
    public void setNomOriginal(String nomOriginal) { this.nomOriginal = nomOriginal; }
    public long getTailleOctets() { return tailleOctets; }
    public void setTailleOctets(long tailleOctets) { this.tailleOctets = tailleOctets; }
    public String getEmpreinteSha256() { return empreinteSha256; }
    public void setEmpreinteSha256(String empreinteSha256) { this.empreinteSha256 = empreinteSha256; }
    public LocalDate getDateDepot() { return dateDepot; }
    public void setDateDepot(LocalDate dateDepot) { this.dateDepot = dateDepot; }
    public StatutMemoire getStatut() { return statut; }
    public void setStatut(StatutMemoire statut) { this.statut = statut; }
    public String getMotifRefus() { return motifRefus; }
    public void setMotifRefus(String motifRefus) { this.motifRefus = motifRefus; }
    public LocalDateTime getDateArchivage() { return dateArchivage; }
    public void setDateArchivage(LocalDateTime dateArchivage) { this.dateArchivage = dateArchivage; }

    /** Pour l'affichage : « 1,2 Mo ». */
    public String getTailleLisible() {
        return tailleOctets >= 1024 * 1024
                ? String.format("%.1f Mo", tailleOctets / (1024.0 * 1024))
                : (tailleOctets / 1024) + " Ko";
    }
}
