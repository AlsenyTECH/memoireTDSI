package com.tdsi.memotheque.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Un mémoire déposé sur la plateforme. [M1] contrat gelé */
public class Memoire {

    private int id;
    private String titre;
    private String resume;
    private String motsCles;               // séparés par des virgules
    private String anneeAcademique;        // ex. 2025-2026
    private Niveau niveau;
    private Domaine domaine;
    private Utilisateur auteur;
    private String coAuteur;
    private Utilisateur encadreur;
    private LocalDate dateSoutenance;
    private Mention mention;
    private LocalDate dateDepot;
    private LocalDate datePublication;     // renseignée à la validation
    private String cheminFichier;
    private String nomOriginal;
    private long tailleOctets;
    private String empreinteSha256;
    private StatutMemoire statut = StatutMemoire.EN_ATTENTE;
    private String motifRefus;
    private Utilisateur validateur;
    private int nbVues;
    private int nbTelechargements;
    private String signature;              // Ed25519 du validateur (Base64), null si non signé
    private Integer idCleSignature;

    public Memoire() { }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getResume() { return resume; }
    public void setResume(String resume) { this.resume = resume; }
    public String getMotsCles() { return motsCles; }
    public void setMotsCles(String motsCles) { this.motsCles = motsCles; }
    public String getAnneeAcademique() { return anneeAcademique; }
    public void setAnneeAcademique(String anneeAcademique) { this.anneeAcademique = anneeAcademique; }
    public Niveau getNiveau() { return niveau; }
    public void setNiveau(Niveau niveau) { this.niveau = niveau; }
    public Domaine getDomaine() { return domaine; }
    public void setDomaine(Domaine domaine) { this.domaine = domaine; }
    public Utilisateur getAuteur() { return auteur; }
    public void setAuteur(Utilisateur auteur) { this.auteur = auteur; }
    public String getCoAuteur() { return coAuteur; }
    public void setCoAuteur(String coAuteur) { this.coAuteur = coAuteur; }
    public Utilisateur getEncadreur() { return encadreur; }
    public void setEncadreur(Utilisateur encadreur) { this.encadreur = encadreur; }
    public LocalDate getDateSoutenance() { return dateSoutenance; }
    public void setDateSoutenance(LocalDate dateSoutenance) { this.dateSoutenance = dateSoutenance; }
    public Mention getMention() { return mention; }
    public void setMention(Mention mention) { this.mention = mention; }
    public LocalDate getDateDepot() { return dateDepot; }
    public void setDateDepot(LocalDate dateDepot) { this.dateDepot = dateDepot; }
    public LocalDate getDatePublication() { return datePublication; }
    public void setDatePublication(LocalDate datePublication) { this.datePublication = datePublication; }
    public String getCheminFichier() { return cheminFichier; }
    public void setCheminFichier(String cheminFichier) { this.cheminFichier = cheminFichier; }
    public String getNomOriginal() { return nomOriginal; }
    public void setNomOriginal(String nomOriginal) { this.nomOriginal = nomOriginal; }
    public long getTailleOctets() { return tailleOctets; }
    public void setTailleOctets(long tailleOctets) { this.tailleOctets = tailleOctets; }
    public String getEmpreinteSha256() { return empreinteSha256; }
    public void setEmpreinteSha256(String empreinteSha256) { this.empreinteSha256 = empreinteSha256; }
    public StatutMemoire getStatut() { return statut; }
    public void setStatut(StatutMemoire statut) { this.statut = statut; }
    public String getMotifRefus() { return motifRefus; }
    public void setMotifRefus(String motifRefus) { this.motifRefus = motifRefus; }
    public Utilisateur getValidateur() { return validateur; }
    public void setValidateur(Utilisateur validateur) { this.validateur = validateur; }
    public int getNbVues() { return nbVues; }
    public void setNbVues(int nbVues) { this.nbVues = nbVues; }
    public int getNbTelechargements() { return nbTelechargements; }
    public void setNbTelechargements(int nbTelechargements) { this.nbTelechargements = nbTelechargements; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public Integer getIdCleSignature() { return idCleSignature; }
    public void setIdCleSignature(Integer idCleSignature) { this.idCleSignature = idCleSignature; }

    // ---- Aides pour les TableView (PropertyValueFactory) : null-safe ----

    public String getDomaineNom() { return domaine == null ? "" : domaine.getNom(); }

    public String getAuteurNomComplet() { return auteur == null ? "" : auteur.getNomComplet(); }

    public String getEncadreurNomComplet() { return encadreur == null ? "" : encadreur.getNomComplet(); }

    /** Vrai si publié depuis moins de 30 jours (badge « Nouveau »). */
    public boolean isNouveau() {
        return datePublication != null
                && ChronoUnit.DAYS.between(datePublication, LocalDate.now()) <= 30;
    }

    @Override
    public String toString() { return titre; }
}
