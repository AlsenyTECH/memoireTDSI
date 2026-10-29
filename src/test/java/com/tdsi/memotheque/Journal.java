package com.tdsi.memotheque;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.tdsi.memotheque.util.DatabaseConnection;

/** Aide de test : compte les lignes du journal d'activité pour une action donnée. */
final class Journal {

    private Journal() { }

    static int compter(String action) throws Exception {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM journal_activite WHERE action = ?")) {
            ps.setString(1, action);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
