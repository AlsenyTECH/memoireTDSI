-- [M1] Schéma de la base MémoTDSI (exécuté au démarrage par DatabaseConnection.initialiser()).
-- Attention : ne pas mettre de point-virgule dans les commentaires.

CREATE TABLE IF NOT EXISTS utilisateur (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    matricule         TEXT UNIQUE,
    nom               TEXT NOT NULL,
    prenom            TEXT NOT NULL,
    email             TEXT NOT NULL UNIQUE,
    mot_de_passe_hash TEXT NOT NULL,
    sel               TEXT NOT NULL,
    role              TEXT NOT NULL CHECK (role IN ('ETUDIANT','ENCADREUR','ADMIN')),
    niveau            TEXT CHECK (niveau IN ('L1','L2','L3','M1','M2')),
    actif             INTEGER NOT NULL DEFAULT 0,
    tentatives_echec  INTEGER NOT NULL DEFAULT 0,
    verrouille        INTEGER NOT NULL DEFAULT 0,
    date_creation     TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS domaine (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    nom         TEXT NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE IF NOT EXISTS memoire (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    titre               TEXT NOT NULL,
    resume              TEXT NOT NULL,
    mots_cles           TEXT NOT NULL,
    annee_academique    TEXT NOT NULL,
    niveau              TEXT NOT NULL CHECK (niveau IN ('L1','L2','L3','M1','M2')),
    id_domaine          INTEGER NOT NULL REFERENCES domaine(id),
    id_auteur           INTEGER NOT NULL REFERENCES utilisateur(id),
    co_auteur           TEXT,
    id_encadreur        INTEGER REFERENCES utilisateur(id),
    date_soutenance     TEXT,
    mention             TEXT CHECK (mention IN ('PASSABLE','ASSEZ_BIEN','BIEN','TRES_BIEN')),
    date_depot          TEXT NOT NULL,
    date_publication    TEXT,
    chemin_fichier      TEXT NOT NULL,
    nom_original        TEXT NOT NULL,
    taille_octets       INTEGER NOT NULL,
    empreinte_sha256    TEXT NOT NULL,
    statut              TEXT NOT NULL DEFAULT 'EN_ATTENTE'
                        CHECK (statut IN ('EN_ATTENTE','VALIDE','REFUSE')),
    motif_refus         TEXT,
    id_validateur       INTEGER REFERENCES utilisateur(id),
    nb_vues             INTEGER NOT NULL DEFAULT 0,
    nb_telechargements  INTEGER NOT NULL DEFAULT 0,
    UNIQUE (id_auteur, niveau, annee_academique)
);

CREATE TABLE IF NOT EXISTS journal_activite (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    id_utilisateur INTEGER REFERENCES utilisateur(id),
    action         TEXT NOT NULL,
    details        TEXT,
    date_heure     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_memoire_statut  ON memoire(statut);
CREATE INDEX IF NOT EXISTS idx_memoire_domaine ON memoire(id_domaine);
CREATE INDEX IF NOT EXISTS idx_memoire_annee   ON memoire(annee_academique);
CREATE INDEX IF NOT EXISTS idx_memoire_niveau  ON memoire(niveau);
