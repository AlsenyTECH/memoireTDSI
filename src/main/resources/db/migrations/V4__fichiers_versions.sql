-- Historique des PDF remplacés lors d'une correction, et recherche rapide des doublons.
CREATE TABLE memoire_version (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    id_memoire       INTEGER NOT NULL REFERENCES memoire(id) ON DELETE CASCADE,
    chemin_fichier   TEXT NOT NULL,
    nom_original     TEXT NOT NULL,
    taille_octets    INTEGER NOT NULL,
    empreinte_sha256 TEXT NOT NULL,
    date_depot       TEXT NOT NULL,
    statut           TEXT NOT NULL,
    motif_refus      TEXT,
    date_archivage   TEXT NOT NULL
);
CREATE INDEX idx_version_memoire ON memoire_version(id_memoire);
CREATE INDEX idx_memoire_empreinte ON memoire(empreinte_sha256);
