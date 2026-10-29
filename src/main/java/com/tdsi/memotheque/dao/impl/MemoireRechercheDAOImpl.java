
package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.tdsi.memotheque.dao.MemoireRechercheDAO;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireCritere;
import com.tdsi.memotheque.model.Mention;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.TriMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.DatabaseConnection;

/**
 * M4 : recherche des mémoires avec filtres, tri et pagination.
 */
public class MemoireRechercheDAOImpl implements MemoireRechercheDAO {

    private static final String SELECT_BASE =
            "SELECT m.*, " +
            "d.nom AS domaine_nom, " +
            "d.description AS domaine_description, " +
            "a.nom AS auteur_nom, " +
            "a.prenom AS auteur_prenom, " +
            "e.nom AS encadreur_nom, " +
            "e.prenom AS encadreur_prenom " +
            "FROM memoire m " +
            "LEFT JOIN domaine d ON d.id = m.id_domaine " +
            "LEFT JOIN utilisateur a ON a.id = m.id_auteur " +
            "LEFT JOIN utilisateur e ON e.id = m.id_encadreur ";

    @Override
    public List<Memoire> rechercher(MemoireCritere c)
            throws SQLException {

        if (c == null) {
            c = new MemoireCritere();
        }

        List<Object> parametres = new ArrayList<>();
        StringBuilder sql = construireWhere(c, parametres);

        TriMemoire tri = c.getTri() == null
                ? TriMemoire.PLUS_RECENTS
                : c.getTri();

        // La clause ORDER BY vient uniquement de l'enum TriMemoire.
        sql.insert(0, SELECT_BASE);
        String requeteFts = requeteFts(c.getMotCle());
        if (tri == TriMemoire.PERTINENCE && requeteFts != null) {
            // bm25 : plus la valeur est basse, plus le document est pertinent.
            sql.append(" ORDER BY (SELECT bm25(memoire_fts) FROM memoire_fts "
                    + "WHERE memoire_fts MATCH ? AND memoire_fts.rowid = m.id), m.id DESC");
            parametres.add(requeteFts);
        } else {
            sql.append(" ORDER BY ").append(tri.getClauseSql());
        }
        sql.append(" LIMIT ? OFFSET ?");

        int taille = Math.max(1, c.getTaille());
        int page = Math.max(1, c.getPage());
        long offset = ((long) page - 1) * taille;

        try (Connection connexion = DatabaseConnection.getConnection();
             PreparedStatement ps = connexion.prepareStatement(sql.toString())) {

            int index = ajouterParametres(ps, parametres);
            ps.setInt(index++, taille);
            ps.setLong(index, offset);

            List<Memoire> resultats = new ArrayList<>();

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultats.add(convertirMemoire(rs));
                }
            }

            return resultats;
        }
    }

    @Override
    public int compter(MemoireCritere c) throws SQLException {

        if (c == null) {
            c = new MemoireCritere();
        }

        List<Object> parametres = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM memoire m ");

        sql.append(construireWhere(c, parametres));

        try (Connection connexion = DatabaseConnection.getConnection();
             PreparedStatement ps = connexion.prepareStatement(sql.toString())) {

            ajouterParametres(ps, parametres);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Construit les filtres SQL et ajoute leurs valeurs séparément.
     */
    private StringBuilder construireWhere(
            MemoireCritere c, List<Object> parametres) {

        StringBuilder sql = new StringBuilder(" WHERE 1 = 1 ");

        if (c.getMotCle() != null && !c.getMotCle().isBlank()) {
            String requete = requeteFts(c.getMotCle());
            if (requete == null) {
                sql.append(" AND 1 = 0 ");          // que de la ponctuation : aucun résultat
            } else {
                // Index plein texte FTS5 (titre, résumé, mots-clés), insensible aux accents.
                sql.append(" AND m.id IN (SELECT rowid FROM memoire_fts WHERE memoire_fts MATCH ?) ");
                parametres.add(requete);
            }
        }

        if (c.getIdDomaine() != null) {
            sql.append(" AND m.id_domaine = ? ");
            parametres.add(c.getIdDomaine());
        }

        if (c.getAnneeAcademique() != null
                && !c.getAnneeAcademique().isBlank()) {
            sql.append(" AND m.annee_academique = ? ");
            parametres.add(c.getAnneeAcademique().trim());
        }

        if (c.getNiveau() != null) {
            sql.append(" AND m.niveau = ? ");
            parametres.add(c.getNiveau().name());
        }

        if (c.getIdEncadreur() != null) {
            sql.append(" AND m.id_encadreur = ? ");
            parametres.add(c.getIdEncadreur());
        }

        if (c.getMention() != null) {
            sql.append(" AND m.mention = ? ");
            parametres.add(c.getMention().name());
        }

        if (c.getDateDebut() != null) {
            sql.append(" AND m.date_publication >= ? ");
            parametres.add(c.getDateDebut().toString());
        }

        if (c.getDateFin() != null) {
            sql.append(" AND m.date_publication <= ? ");
            parametres.add(c.getDateFin().toString());
        }

        if (c.getStatut() != null) {
            sql.append(" AND m.statut = ? ");
            parametres.add(c.getStatut().name());
        }

        if (c.getIdAuteur() != null) {
            sql.append(" AND m.id_auteur = ? ");
            parametres.add(c.getIdAuteur());
        }

        return sql;
    }

    /**
     * Transforme la saisie libre en requête FTS5 sûre : chaque mot (lettres et chiffres)
     * devient un terme entre guillemets avec recherche par préfixe, tous les mots sont exigés.
     * « sécu réseau » -> "sécu"* "réseau"*. Les opérateurs FTS (OR, NEAR, *, :) saisis par
     * l'utilisateur sont neutralisés. Retourne null s'il n'y a aucun mot.
     */
    static String requeteFts(String saisie) {
        if (saisie == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String mot : saisie.split("[^\\p{L}\\p{N}]+")) {
            if (mot.isEmpty() || n >= 10) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append('"').append(mot.length() > 50 ? mot.substring(0, 50) : mot).append("\"*");
            n++;
        }
        return n == 0 ? null : sb.toString();
    }

    private int ajouterParametres(
            PreparedStatement ps, List<Object> parametres)
            throws SQLException {

        int index = 1;

        for (Object valeur : parametres) {
            ps.setObject(index++, valeur);
        }

        return index;
    }

    /**
     * Convertit une ligne SQL en objet Memoire.
     */
    private Memoire convertirMemoire(ResultSet rs) throws SQLException {

        Memoire m = new Memoire();

        m.setId(rs.getInt("id"));
        m.setTitre(rs.getString("titre"));
        m.setResume(rs.getString("resume"));
        m.setMotsCles(rs.getString("mots_cles"));
        m.setAnneeAcademique(rs.getString("annee_academique"));
        m.setNiveau(enumValeur(
                Niveau.class, rs.getString("niveau")));

        m.setCoAuteur(rs.getString("co_auteur"));

        m.setDateSoutenance(dateValeur(
                rs.getString("date_soutenance")));
        m.setMention(enumValeur(
                Mention.class, rs.getString("mention")));

        m.setDateDepot(dateValeur(rs.getString("date_depot")));
        m.setDatePublication(dateValeur(
                rs.getString("date_publication")));

        m.setCheminFichier(rs.getString("chemin_fichier"));
        m.setNomOriginal(rs.getString("nom_original"));
        m.setTailleOctets(rs.getLong("taille_octets"));
        m.setEmpreinteSha256(rs.getString("empreinte_sha256"));

        m.setStatut(enumValeur(
                StatutMemoire.class, rs.getString("statut")));

        m.setMotifRefus(rs.getString("motif_refus"));
        m.setNbVues(rs.getInt("nb_vues"));
        m.setNbTelechargements(rs.getInt("nb_telechargements"));

        // Domaine associé
        int idDomaine = rs.getInt("id_domaine");
        if (!rs.wasNull()) {
            Domaine domaine = new Domaine();
            domaine.setId(idDomaine);
            domaine.setNom(rs.getString("domaine_nom"));
            domaine.setDescription(
                    rs.getString("domaine_description"));
            m.setDomaine(domaine);
        }

        // Auteur associé
        int idAuteur = rs.getInt("id_auteur");
        if (!rs.wasNull()) {
            Utilisateur auteur = new Utilisateur();
            auteur.setId(idAuteur);
            auteur.setNom(rs.getString("auteur_nom"));
            auteur.setPrenom(rs.getString("auteur_prenom"));
            m.setAuteur(auteur);
        }

        // Encadreur associé (facultatif)
        int idEncadreur = rs.getInt("id_encadreur");
        if (!rs.wasNull()) {
            Utilisateur encadreur = new Utilisateur();
            encadreur.setId(idEncadreur);
            encadreur.setNom(rs.getString("encadreur_nom"));
            encadreur.setPrenom(rs.getString("encadreur_prenom"));
            m.setEncadreur(encadreur);
        }

        // Validateur : on renseigne son identifiant s'il existe.
        int idValidateur = rs.getInt("id_validateur");
        if (!rs.wasNull()) {
            Utilisateur validateur = new Utilisateur();
            validateur.setId(idValidateur);
            m.setValidateur(validateur);
        }

        return m;
    }

    private LocalDate dateValeur(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        return LocalDate.parse(valeur);
    }

    private <E extends Enum<E>> E enumValeur(
            Class<E> type, String valeur) {

        if (valeur == null || valeur.isBlank()) {
            return null;
        }

        return Enum.valueOf(type, valeur);
    }

    @Override
    public List<String> listerAnneesAcademiques()
            throws SQLException {

        String sql =
                "SELECT DISTINCT annee_academique " +
                "FROM memoire " +
                "WHERE statut = 'VALIDE' " +
                "ORDER BY annee_academique DESC";

        List<String> annees = new ArrayList<>();

        try (Connection connexion = DatabaseConnection.getConnection();
             PreparedStatement ps = connexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                annees.add(rs.getString("annee_academique"));
            }
        }

        return annees;
    }

    @Override
    public void incrementerVues(int idMemoire) throws SQLException {
        incrementerCompteur("nb_vues", idMemoire);
    }

    @Override
    public void incrementerTelechargements(int idMemoire)
            throws SQLException {
        incrementerCompteur("nb_telechargements", idMemoire);
    }

    /**
     * Le nom de colonne est choisi uniquement par le code interne.
     */
    private void incrementerCompteur(
            String colonne, int idMemoire) throws SQLException {

        if (!"nb_vues".equals(colonne)
                && !"nb_telechargements".equals(colonne)) {
            throw new IllegalArgumentException(
                    "Compteur non autorisé");
        }

        String sql = "UPDATE memoire SET " + colonne
                + " = " + colonne + " + 1 WHERE id = ?";

        try (Connection connexion = DatabaseConnection.getConnection();
             PreparedStatement ps = connexion.prepareStatement(sql)) {

            ps.setInt(1, idMemoire);
            ps.executeUpdate();
        }
    }
}
