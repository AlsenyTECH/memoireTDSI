package com.tdsi.memotheque.model;

import java.time.LocalDateTime;

/**
 * Paire de clés Ed25519 d'un validateur (encadreur ou admin).
 * La clé publique est en clair (X.509, Base64) ; la clé privée (PKCS#8) est chiffrée
 * en AES-256-GCM avec une clé dérivée du mot de passe du validateur (PBKDF2).
 * Une clé désactivée (mot de passe réinitialisé) reste conservée pour vérifier les anciennes signatures.
 */
public class CleSignature {

    private int id;
    private int idUtilisateur;
    private String algorithme = "Ed25519";
    private String clePublique;
    private String clePriveeChiffree;
    private String sel;
    private int iterations;
    private boolean active = true;
    private LocalDateTime dateCreation;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getIdUtilisateur() { return idUtilisateur; }
    public void setIdUtilisateur(int idUtilisateur) { this.idUtilisateur = idUtilisateur; }
    public String getAlgorithme() { return algorithme; }
    public void setAlgorithme(String algorithme) { this.algorithme = algorithme; }
    public String getClePublique() { return clePublique; }
    public void setClePublique(String clePublique) { this.clePublique = clePublique; }
    public String getClePriveeChiffree() { return clePriveeChiffree; }
    public void setClePriveeChiffree(String clePriveeChiffree) { this.clePriveeChiffree = clePriveeChiffree; }
    public String getSel() { return sel; }
    public void setSel(String sel) { this.sel = sel; }
    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
}
