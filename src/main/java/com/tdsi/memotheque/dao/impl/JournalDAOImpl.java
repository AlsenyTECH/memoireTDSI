package com.tdsi.memotheque.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.tdsi.memotheque.dao.JournalDAO;
import com.tdsi.memotheque.model.JournalActivite;
import com.tdsi.memotheque.util.DatabaseConnection;

/** [M6] Implémentation JDBC de JournalDAO. */
public class JournalDAOImpl implements JournalDAO {

    
    @Override
    public void enregistrer(JournalActivite j) throws SQLException {
        String sql = """
            INSERT INTO journal_activite
            (id_utilisateur, action, details, date_heure)
            VALUES (?, ?, ?, ?)
            """;

        if (j.getDateHeure() == null) {
            j.setDateHeure(LocalDateTime.now());
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (j.getIdUtilisateur() == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, j.getIdUtilisateur());
            }

            ps.setString(2, j.getAction());
            ps.setString(3, j.getDetails());
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            	ps.setString(4, j.getDateHeure().format(formatter));
            

            ps.executeUpdate();
        }
    }

    @Override
    public List<JournalActivite> derniers(int limite) throws SQLException {
        if (limite < 1) {
            throw new IllegalArgumentException(
                "La limite doit être supérieure à zéro."
            );
        }

        int nombre = Math.min(limite, 200);

        String sql = """
            SELECT id, id_utilisateur, action, details, date_heure
            FROM journal_activite
            ORDER BY date_heure DESC, id DESC
            LIMIT ?
            """;

        List<JournalActivite> journal = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, nombre);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JournalActivite j = new JournalActivite();

                    j.setId(rs.getInt("id"));

                    int idUtilisateur = rs.getInt("id_utilisateur");
                    j.setIdUtilisateur(
                        rs.wasNull() ? null : idUtilisateur
                    );

                    j.setAction(rs.getString("action"));
                    j.setDetails(rs.getString("details"));
                    j.setDateHeure(
                    		LocalDateTime.parse(rs.getString("date_heure"),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    );

                    journal.add(j);
                }
            }
        }

        return journal;
    }
}
