package com.tdsi.memotheque.util;

import java.util.Locale;

import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Soutenance;

/** Textes d'affichage partagés par les écrans de suivi (soutenance, résultat). */
public final class Libelles {

    private static final java.time.format.DateTimeFormatter JJ_MM_AAAA =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Libelles() { }

    /** Date au format français, ou « - ». */
    public static String date(java.time.LocalDate d) {
        return d == null ? "-" : d.format(JJ_MM_AAAA);
    }

    /** « 12/06/2026 à 10h00 — Amphi A », ou la date de soutenance enregistrée, ou « - ». */
    public static String soutenance(Memoire m, Soutenance s) {
        if (s != null) {
            return s.getDateHeureLisible() + " — " + s.getSalle();
        }
        return m.getDateSoutenance() == null ? "-" : "Soutenu le " + date(m.getDateSoutenance());
    }

    /** « 14,50/20 — Bien », « 8,00/20 (ajourné) » ou « - ». */
    public static String resultat(Memoire m) {
        if (m.getNote() == null) {
            return "-";
        }
        String note = String.format(Locale.FRANCE, "%.2f/20", m.getNote());
        return m.getMention() == null ? note + " (ajourné)" : note + " — " + m.getMention().getLibelle();
    }
}
