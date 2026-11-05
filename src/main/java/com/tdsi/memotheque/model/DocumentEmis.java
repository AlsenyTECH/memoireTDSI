package com.tdsi.memotheque.model;

import java.time.LocalDateTime;

/** Document officiel émis et signé (registre de vérification). */
public record DocumentEmis(String code, String type, int idReference, String sujet, String contenu,
        String signature, String empreinteEtat, LocalDateTime dateEmission, Integer idEmetteur, boolean revoque) {

    /** Code lisible : « K7F2-9QXA-3MTP-W8LD ». */
    public String codeLisible() {
        return code.replaceAll("(.{4})(?!$)", "$1-");
    }
}
