package com.tdsi.memotheque.model;

/**
 * Résultat de la vérification de la chaîne HMAC du journal d'activité.
 *
 * @param intact               vrai si aucune altération n'est détectée
 * @param lignesVerifiees      lignes protégées dont le HMAC est correct
 * @param lignesNonProtegees   lignes antérieures à l'activation du chaînage (sans HMAC)
 * @param premiereLigneInvalide id de la première ligne altérée (null si intact ou si l'anomalie est en fin de journal)
 * @param message              explication lisible
 */
public record VerificationJournal(boolean intact, int lignesVerifiees, int lignesNonProtegees,
        Integer premiereLigneInvalide, String message) {
}
