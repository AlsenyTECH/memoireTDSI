package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.tdsi.memotheque.dao.MemoireDAO;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Mention;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.DatabaseConnection;

/**
 * [M3] Implémentation JDBC du CRUD des mémoires.
 * RAPPEL : PreparedStatement uniquement, jamais de concaténation de saisie dans le SQL.
 */
public class MemoireDAOImpl implements MemoireDAO {

    /** Requête commune : le mémoire avec son domaine, son auteur, son encadreur et son validateur. */
    static final String SELECT_COMPLET = """
            SELECT m.*,
                   d.nom AS domaine_nom, d.description AS domaine_description,
                   a.nom AS auteur_nom, a.prenom AS auteur_prenom,
                   a.email AS auteur_email, a.matricule AS auteur_matricule,
                   e.nom AS encadreur_nom, e.prenom AS encadreur_prenom,
                   v.nom AS validateur_nom, v.prenom AS validateur_prenom
            FROM memoire m
            LEFT JOIN domaine d ON d.id = m.id_domaine
            LEFT JOIN utilisateur a ON a.id = m.id_auteur
            LEFT JOIN utilisateur e ON e.id = m.id_encadreur
            LEFT JOIN utilisateur v ON v.id = m.id_validateur
            """;

    @Override
    public void insert(Memoire m) throws SQLException {
        String sql = """
                INSERT INTO memoire
                (titre, resume, mots_cles, annee_academique, niveau, id_domaine, id_auteur,
                 co_auteur, id_encadreur, date_soutenance, mention, date_depot, date_publication,
                 chemin_fichier, nom_original, taille_octets, empreinte_sha256, statut,
                 motif_refus, id_validateur, nb_vues, nb_telechargements)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            int i = remplirChamps(ps, m);
            ps.setInt(i++, m.getNbVues());
            ps.setInt(i, m.getNbTelechargements());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    m.setId(rs.getInt(1));
                } else {
                    throw new SQLException("Impossible de récupérer l'identifiant généré.");
                }
            }
        }
    }

    @Override
    public void update(Memoire m) throws SQLException {
        String sql = """
                UPDATE memoire
                SET titre = ?, resume = ?, mots_cles = ?, annee_academique = ?, niveau = ?,
                    id_domaine = ?, id_auteur = ?, co_auteur = ?, id_encadreur = ?,
                    date_soutenance = ?, mention = ?, date_depot = ?, date_publication = ?,
                    chemin_fichier = ?, nom_original = ?, taille_octets = ?, empreinte_sha256 = ?,
                    statut = ?, motif_refus = ?, id_validateur = ?
                WHERE id = ?
                """;

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int i = remplirChamps(ps, m);
            ps.setInt(i, m.getId());

            if (ps.executeUpdate() == 0) {
                throw new SQLException("Mémoire introuvable : " + m.getId());
            }
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM memoire WHERE id = ?";

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, id);

            if (ps.executeUpdate() == 0) {
                throw new SQLException("Mémoire introuvable : " + id);
            }
        }
    }

    @Override
    public Memoire findById(int id) throws SQLException {
        String sql = SELECT_COMPLET + " WHERE m.id = ?";

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapperMemoire(rs) : null;
            }
        }
    }

    @Override
    public List<Memoire> findAll() throws SQLException {
        return lister(SELECT_COMPLET + " ORDER BY m.date_depot DESC, m.id DESC", null);
    }

    @Override
    public List<Memoire> findByAuteur(int idAuteur) throws SQLException {
        return lister(SELECT_COMPLET
                + " WHERE m.id_auteur = ? ORDER BY m.date_depot DESC, m.id DESC", idAuteur);
    }

    @Override
    public boolean existePourAuteurNiveauAnnee(int idAuteur, Niveau n, String annee)
            throws SQLException {

        String sql = """
                SELECT 1 FROM memoire
                WHERE id_auteur = ? AND niveau = ? AND annee_academique = ?
                LIMIT 1
                """;

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, idAuteur);
            ps.setString(2, n == null ? null : n.name());
            ps.setString(3, annee);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<Memoire> lister(String sql, Integer parametre) throws SQLException {
        List<Memoire> memoires = new ArrayList<>();

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            if (parametre != null) {
                ps.setInt(1, parametre);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    memoires.add(mapperMemoire(rs));
                }
            }
        }

        return memoires;
    }

    /** Remplit les 20 premières colonnes communes à INSERT et UPDATE. Retourne l'index suivant. */
    private int remplirChamps(PreparedStatement ps, Memoire m) throws SQLException {
        int i = 1;
        ps.setString(i++, m.getTitre());
        ps.setString(i++, m.getResume());
        ps.setString(i++, m.getMotsCles());
        ps.setString(i++, m.getAnneeAcademique());
        ps.setString(i++, m.getNiveau() == null ? null : m.getNiveau().name());
        ps.setInt(i++, m.getDomaine().getId());
        ps.setInt(i++, m.getAuteur().getId());
        ps.setString(i++, m.getCoAuteur());
        setIdOuNull(ps, i++, m.getEncadreur());
        ps.setString(i++, dateOuNull(m.getDateSoutenance()));
        ps.setString(i++, m.getMention() == null ? null : m.getMention().name());
        ps.setString(i++, dateOuNull(m.getDateDepot()));
        ps.setString(i++, dateOuNull(m.getDatePublication()));
        ps.setString(i++, m.getCheminFichier());
        ps.setString(i++, m.getNomOriginal());
        ps.setLong(i++, m.getTailleOctets());
        ps.setString(i++, m.getEmpreinteSha256());
        ps.setString(i++, (m.getStatut() == null ? StatutMemoire.EN_ATTENTE : m.getStatut()).name());
        ps.setString(i++, m.getMotifRefus());
        setIdOuNull(ps, i++, m.getValidateur());
        return i;
    }

    private void setIdOuNull(PreparedStatement ps, int index, Utilisateur u) throws SQLException {
        if (u == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, u.getId());
        }
    }

    private String dateOuNull(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /** Convertit une ligne de SELECT_COMPLET en Memoire (réutilisé par les autres DAO du package). */
    static Memoire mapperMemoire(ResultSet rs) throws SQLException {
        Memoire m = new Memoire();

        m.setId(rs.getInt("id"));
        m.setTitre(rs.getString("titre"));
        m.setResume(rs.getString("resume"));
        m.setMotsCles(rs.getString("mots_cles"));
        m.setAnneeAcademique(rs.getString("annee_academique"));
        m.setNiveau(enumOuNull(Niveau.class, rs.getString("niveau")));
        m.setCoAuteur(rs.getString("co_auteur"));
        m.setDateSoutenance(dateOuNull(rs.getString("date_soutenance")));
        m.setMention(enumOuNull(Mention.class, rs.getString("mention")));
        m.setDateDepot(dateOuNull(rs.getString("date_depot")));
        m.setDatePublication(dateOuNull(rs.getString("date_publication")));
        m.setCheminFichier(rs.getString("chemin_fichier"));
        m.setNomOriginal(rs.getString("nom_original"));
        m.setTailleOctets(rs.getLong("taille_octets"));
        m.setEmpreinteSha256(rs.getString("empreinte_sha256"));
        m.setStatut(enumOuNull(StatutMemoire.class, rs.getString("statut")));
        m.setMotifRefus(rs.getString("motif_refus"));
        m.setNbVues(rs.getInt("nb_vues"));
        m.setNbTelechargements(rs.getInt("nb_telechargements"));

        int idDomaine = rs.getInt("id_domaine");
        if (!rs.wasNull()) {
            m.setDomaine(new Domaine(idDomaine, rs.getString("domaine_nom"),
                    rs.getString("domaine_description")));
        }

        int idAuteur = rs.getInt("id_auteur");
        if (!rs.wasNull()) {
            Utilisateur auteur = personne(idAuteur,
                    rs.getString("auteur_nom"), rs.getString("auteur_prenom"));
            auteur.setEmail(rs.getString("auteur_email"));
            auteur.setMatricule(rs.getString("auteur_matricule"));
            m.setAuteur(auteur);
        }

        int idEncadreur = rs.getInt("id_encadreur");
        if (!rs.wasNull()) {
            m.setEncadreur(personne(idEncadreur,
                    rs.getString("encadreur_nom"), rs.getString("encadreur_prenom")));
        }

        int idValidateur = rs.getInt("id_validateur");
        if (!rs.wasNull()) {
            m.setValidateur(personne(idValidateur,
                    rs.getString("validateur_nom"), rs.getString("validateur_prenom")));
        }

        return m;
    }

    private static Utilisateur personne(int id, String nom, String prenom) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setNom(nom);
        u.setPrenom(prenom);
        return u;
    }

    private static LocalDate dateOuNull(String valeur) {
        return valeur == null || valeur.isBlank() ? null : LocalDate.parse(valeur);
    }

    private static <E extends Enum<E>> E enumOuNull(Class<E> type, String valeur) {
        return valeur == null || valeur.isBlank() ? null : Enum.valueOf(type, valeur);
    }
}
