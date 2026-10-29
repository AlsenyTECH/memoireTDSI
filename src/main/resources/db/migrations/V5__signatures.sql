-- Signature Ed25519 des mémoires validés.
-- La clé privée de chaque validateur est chiffrée (AES-256-GCM) avec une clé dérivée
-- de son mot de passe (PBKDF2) : elle n'est jamais stockée en clair.
CREATE TABLE cle_signature (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    id_utilisateur      INTEGER NOT NULL REFERENCES utilisateur(id),
    algorithme          TEXT NOT NULL DEFAULT 'Ed25519',
    cle_publique        TEXT NOT NULL,
    cle_privee_chiffree TEXT NOT NULL,
    sel                 TEXT NOT NULL,
    iterations          INTEGER NOT NULL,
    active              INTEGER NOT NULL DEFAULT 1,
    date_creation       TEXT NOT NULL
);
CREATE INDEX idx_cle_utilisateur ON cle_signature(id_utilisateur, active);

ALTER TABLE memoire ADD COLUMN signature TEXT;
ALTER TABLE memoire ADD COLUMN id_cle_signature INTEGER REFERENCES cle_signature(id);
