package com.tdsi.memotheque.dao.impl;

import java.sql.SQLException;
import java.util.List;

import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.sql.Statement;

import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.util.DatabaseConnection;

/**
 * [M2] Implémentation JDBC de UtilisateurDAO.
 * RAPPEL : PreparedStatement uniquement, jamais de concaténation de saisie dans
 * le SQL.
 */
public class UtilisateurDAOImpl implements UtilisateurDAO {
    @Override
    public void insert(Utilisateur u) throws SQLException {
        String sql = """
                INSERT INTO utilisateur
                (matricule, nom, prenom, email, mot_de_passe_hash, sel,
                 role, niveau, actif, tentatives_echec, verrouille, date_creation)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection c = DatabaseConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, u.getMatricule());
            ps.setString(2, u.getNom());
            ps.setString(3, u.getPrenom());
            ps.setString(4, u.getEmail());
            ps.setString(5, u.getMotDePasseHash());
            ps.setString(6, u.getSel());
            ps.setString(7, u.getRole().name());

            if (u.getNiveau() != null) {
                ps.setString(8, u.getNiveau().name());
            } else {
                ps.setNull(8, java.sql.Types.VARCHAR);
            }

            ps.setInt(9, u.isActif() ? 1 : 0);
            ps.setInt(10, u.getTentativesEchec());
            ps.setInt(11, u.isVerrouille() ? 1 : 0);
            ps.setString(12, u.getDateCreation().toString());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    u.setId(rs.getInt(1));
                } else {
                    throw new SQLException(
                            "Impossible de récupérer l'identifiant généré.");
                }
            }
        }
    }

    @Override
    public void update(Utilisateur u) throws SQLException {
        String sql = """
                UPDATE utilisateur
                SET matricule = ?, nom = ?, prenom = ?, email = ?,
                    mot_de_passe_hash = ?, sel = ?, role = ?, niveau = ?,
                    actif = ?, tentatives_echec = ?, verrouille = ?,
                    date_creation = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, u.getMatricule());
            ps.setString(2, u.getNom());
            ps.setString(3, u.getPrenom());
            ps.setString(4, u.getEmail());
            ps.setString(5, u.getMotDePasseHash());
            ps.setString(6, u.getSel());
            ps.setString(7, u.getRole().name());

            if (u.getNiveau() != null) {
                ps.setString(8, u.getNiveau().name());
            } else {
                ps.setNull(8, java.sql.Types.VARCHAR);
            }

            ps.setInt(9, u.isActif() ? 1 : 0);
            ps.setInt(10, u.getTentativesEchec());
            ps.setInt(11, u.isVerrouille() ? 1 : 0);
            ps.setString(12, u.getDateCreation().toString());
            ps.setInt(13, u.getId());

            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM utilisateur WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            ps.executeUpdate();
        }
    }

    private Utilisateur mapperUtilisateur(ResultSet rs) throws SQLException {
        Utilisateur u = new Utilisateur();

        u.setId(rs.getInt("id"));
        u.setMatricule(rs.getString("matricule"));
        u.setNom(rs.getString("nom"));
        u.setPrenom(rs.getString("prenom"));
        u.setEmail(rs.getString("email"));
        u.setMotDePasseHash(rs.getString("mot_de_passe_hash"));
        u.setSel(rs.getString("sel"));

        u.setRole(Role.valueOf(rs.getString("role")));

        String niveau = rs.getString("niveau");
        if (niveau != null) {
            u.setNiveau(Niveau.valueOf(niveau));
        }

        u.setActif(rs.getInt("actif") == 1);
        u.setTentativesEchec(rs.getInt("tentatives_echec"));
        u.setVerrouille(rs.getInt("verrouille") == 1);

        String date = rs.getString("date_creation");
        if (date != null) {
            u.setDateCreation(LocalDate.parse(date));
        }

        return u;
    }

    @Override
    public Utilisateur findById(int id) throws SQLException {
        String sql = "SELECT * FROM utilisateur WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapperUtilisateur(rs);
                }
            }
        }

        return null;
    }

    @Override
    public List<Utilisateur> findAll() throws SQLException {
        String sql = "SELECT * FROM utilisateur ORDER BY id";

        List<Utilisateur> utilisateurs = new java.util.ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                utilisateurs.add(mapperUtilisateur(rs));
            }
        }

        return utilisateurs;
    }

    @Override
    public Utilisateur findByEmailOuMatricule(String identifiant) throws SQLException {
        String sql = """
                SELECT * FROM utilisateur
                WHERE email = ? OR matricule = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, identifiant);
            ps.setString(2, identifiant);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapperUtilisateur(rs);
                }
            }
        }

        return null;
    }

    @Override
    public boolean existeMatricule(String matricule) throws SQLException {
        String sql = "SELECT 1 FROM utilisateur WHERE matricule = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, matricule);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean existeEmail(String email) throws SQLException {
        String sql = "SELECT 1 FROM utilisateur WHERE email = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public List<Utilisateur> findByRole(Role role) throws SQLException {
        String sql = "SELECT * FROM utilisateur WHERE role = ? ORDER BY id";

        List<Utilisateur> utilisateurs = new java.util.ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, role.name());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    utilisateurs.add(mapperUtilisateur(rs));
                }
            }
        }

        return utilisateurs;
    }

    @Override
    public void changerActivation(int id, boolean actif) throws SQLException {
        String sql = "UPDATE utilisateur SET actif = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, actif ? 1 : 0);
            ps.setInt(2, id);

            ps.executeUpdate();
        }
    }

    @Override
    public void mettreAJourTentatives(int id, int tentatives, boolean verrouille)
            throws SQLException {

        String sql = """
                UPDATE utilisateur
                SET tentatives_echec = ?, verrouille = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, tentatives);
            ps.setInt(2, verrouille ? 1 : 0);
            ps.setInt(3, id);

            ps.executeUpdate();
        }
    }

    @Override
    public void changerMotDePasse(int id, String hash, String sel) throws SQLException {
        String sql = """
                UPDATE utilisateur
                SET mot_de_passe_hash = ?, sel = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, hash);
            ps.setString(2, sel);
            ps.setInt(3, id);

            ps.executeUpdate();
        }
    }

}
