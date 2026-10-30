package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tdsi.memotheque.dao.SoutenanceDAO;
import com.tdsi.memotheque.model.MembreJury;
import com.tdsi.memotheque.model.RoleJury;
import com.tdsi.memotheque.model.Soutenance;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.DatabaseConnection;

/** Implémentation JDBC des soutenances et jurys. */
public class SoutenanceDAOImpl implements SoutenanceDAO {

    /** Mémoire complet + colonnes de la soutenance (préfixées s_). */
    private static final String SELECT = MemoireDAOImpl.SELECT_COMPLET.replace("SELECT m.*,",
            "SELECT m.*, s.date_heure AS s_date_heure, s.salle AS s_salle, s.statut AS s_statut, "
                    + "s.observations AS s_observations, s.id_planificateur AS s_id_planificateur, "
                    + "s.date_planification AS s_date_planification,")
            + " JOIN soutenance s ON s.id_memoire = m.id ";

    @Override
    public void enregistrer(Soutenance s) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO soutenance
                        (id_memoire, date_heure, salle, statut, observations, id_planificateur, date_planification)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(id_memoire) DO UPDATE SET
                            date_heure = excluded.date_heure, salle = excluded.salle, statut = excluded.statut,
                            observations = excluded.observations, id_planificateur = excluded.id_planificateur,
                            date_planification = excluded.date_planification
                        """)) {
                    ps.setInt(1, s.getIdMemoire());
                    ps.setString(2, s.getDateHeure().withSecond(0).withNano(0).toString());
                    ps.setString(3, s.getSalle());
                    ps.setString(4, s.getStatut().name());
                    ps.setString(5, s.getObservations());
                    if (s.getIdPlanificateur() == null) {
                        ps.setNull(6, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(6, s.getIdPlanificateur());
                    }
                    ps.setString(7, (s.getDatePlanification() == null ? LocalDateTime.now()
                            : s.getDatePlanification()).withNano(0).toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM jury_membre WHERE id_memoire = ?")) {
                    ps.setInt(1, s.getIdMemoire());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO jury_membre (id_memoire, id_enseignant, role) VALUES (?, ?, ?)")) {
                    for (MembreJury j : s.getJury()) {
                        ps.setInt(1, s.getIdMemoire());
                        ps.setInt(2, j.enseignant().getId());
                        ps.setString(3, j.role().name());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Override
    public Soutenance findByMemoire(int idMemoire) throws SQLException {
        List<Soutenance> l = lister(SELECT + " WHERE m.id = ?", idMemoire);
        return l.isEmpty() ? null : l.get(0);
    }

    @Override
    public List<Soutenance> findAll() throws SQLException {
        return lister(SELECT + " ORDER BY s.date_heure", null);
    }

    @Override
    public List<Soutenance> findByEnseignant(int idEnseignant) throws SQLException {
        return lister(SELECT + " WHERE m.id IN (SELECT id_memoire FROM jury_membre WHERE id_enseignant = ?)"
                + " ORDER BY s.date_heure", idEnseignant);
    }

    @Override
    public void marquerTenue(int idMemoire, String observations) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE soutenance SET statut = 'TENUE', observations = ? WHERE id_memoire = ?")) {
            ps.setString(1, observations);
            ps.setInt(2, idMemoire);
            ps.executeUpdate();
        }
    }

    @Override
    public void supprimer(int idMemoire) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM soutenance WHERE id_memoire = ?")) {
            ps.setInt(1, idMemoire);
            ps.executeUpdate();
        }
    }

    @Override
    public List<Soutenance> conflits(LocalDateTime debut, LocalDateTime fin, String salle,
            List<Integer> enseignants, int idMemoireExclu) throws SQLException {
        List<Soutenance> resultat = new ArrayList<>();
        for (Soutenance s : findAll()) {
            if (s.getIdMemoire() == idMemoireExclu || s.getStatut() == Soutenance.Statut.TENUE) {
                continue;
            }
            LocalDateTime d = s.getDateHeure();
            LocalDateTime f = d.plusMinutes(Soutenance.DUREE_MINUTES);
            boolean chevauche = d.isBefore(fin) && debut.isBefore(f);
            if (!chevauche) {
                continue;
            }
            boolean memeSalle = s.getSalle().trim().equalsIgnoreCase(salle.trim());
            boolean memeEnseignant = s.getJury().stream()
                    .anyMatch(j -> j.enseignant() != null && enseignants.contains(j.enseignant().getId()));
            if (memeSalle || memeEnseignant) {
                resultat.add(s);
            }
        }
        return resultat;
    }

    // ------------------------------------------------------------------ outils

    private List<Soutenance> lister(String sql, Integer parametre) throws SQLException {
        Map<Integer, Soutenance> parMemoire = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (parametre != null) {
                    ps.setInt(1, parametre);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Soutenance s = new Soutenance();
                        s.setMemoire(MemoireDAOImpl.mapperMemoire(rs));
                        s.setDateHeure(LocalDateTime.parse(rs.getString("s_date_heure")));
                        s.setSalle(rs.getString("s_salle"));
                        s.setStatut(Soutenance.Statut.valueOf(rs.getString("s_statut")));
                        s.setObservations(rs.getString("s_observations"));
                        int idPlan = rs.getInt("s_id_planificateur");
                        s.setIdPlanificateur(rs.wasNull() ? null : idPlan);
                        s.setDatePlanification(LocalDateTime.parse(rs.getString("s_date_planification")));
                        parMemoire.put(s.getIdMemoire(), s);
                    }
                }
            }
            if (!parMemoire.isEmpty()) {
                chargerJurys(c, parMemoire);
            }
        }
        return new ArrayList<>(parMemoire.values());
    }

    private void chargerJurys(Connection c, Map<Integer, Soutenance> parMemoire) throws SQLException {
        String sql = """
                SELECT j.id_memoire, j.role, u.id, u.nom, u.prenom, u.email
                FROM jury_membre j JOIN utilisateur u ON u.id = j.id_enseignant
                ORDER BY CASE j.role WHEN 'PRESIDENT' THEN 1 WHEN 'EXAMINATEUR' THEN 2 ELSE 3 END, u.nom
                """;
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Soutenance s = parMemoire.get(rs.getInt("id_memoire"));
                if (s == null) {
                    continue;
                }
                Utilisateur u = new Utilisateur();
                u.setId(rs.getInt("id"));
                u.setNom(rs.getString("nom"));
                u.setPrenom(rs.getString("prenom"));
                u.setEmail(rs.getString("email"));
                s.getJury().add(new MembreJury(u, RoleJury.valueOf(rs.getString("role"))));
            }
        }
    }
}
