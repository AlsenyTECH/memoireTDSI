-- Notifications internes (dépôt, validation, refus, activation de compte...).
CREATE TABLE notification (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    id_utilisateur INTEGER NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    message        TEXT NOT NULL,
    lue            INTEGER NOT NULL DEFAULT 0,
    date_heure     TEXT NOT NULL
);
CREATE INDEX idx_notification_utilisateur ON notification(id_utilisateur, lue);
