-- @reconstruction (exécuté avec les clés étrangères désactivées : voir Migrations)
-- Circuit universitaire complet :
--   EN_ATTENTE (soumis à l'encadreur) -> REFUSE (corrections demandées) ou AUTORISE (autorisé à soutenir)
--   -> soutenance planifiée par la scolarité -> PV (note, mention) -> VALIDE (publié au catalogue).
-- SQLite ne permet pas de modifier une contrainte CHECK : la table memoire est reconstruite
-- (procédure officielle : nouvelle table, copie, suppression, renommage).

CREATE TABLE memoire_nouveau (
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
                        CHECK (statut IN ('EN_ATTENTE','REFUSE','AUTORISE','VALIDE')),
    motif_refus         TEXT,
    id_validateur       INTEGER REFERENCES utilisateur(id),
    nb_vues             INTEGER NOT NULL DEFAULT 0,
    nb_telechargements  INTEGER NOT NULL DEFAULT 0,
    signature           TEXT,
    id_cle_signature    INTEGER REFERENCES cle_signature(id),
    niveau_diffusion    TEXT NOT NULL DEFAULT 'PUBLIC'
                        CHECK (niveau_diffusion IN ('PUBLIC','RESTREINT','CONFIDENTIEL')),
    date_fin_embargo    TEXT,
    date_avis           TEXT,
    note                REAL CHECK (note IS NULL OR (note >= 0 AND note <= 20)),
    UNIQUE (id_auteur, niveau, annee_academique)
);

INSERT INTO memoire_nouveau
    (id, titre, resume, mots_cles, annee_academique, niveau, id_domaine, id_auteur, co_auteur,
     id_encadreur, date_soutenance, mention, date_depot, date_publication, chemin_fichier,
     nom_original, taille_octets, empreinte_sha256, statut, motif_refus, id_validateur,
     nb_vues, nb_telechargements, signature, id_cle_signature)
SELECT
     id, titre, resume, mots_cles, annee_academique, niveau, id_domaine, id_auteur, co_auteur,
     id_encadreur, date_soutenance, mention, date_depot, date_publication, chemin_fichier,
     nom_original, taille_octets, empreinte_sha256, statut, motif_refus, id_validateur,
     nb_vues, nb_telechargements, signature, id_cle_signature
FROM memoire;

DROP TABLE memoire;
ALTER TABLE memoire_nouveau RENAME TO memoire;

CREATE INDEX idx_memoire_statut    ON memoire(statut);
CREATE INDEX idx_memoire_domaine   ON memoire(id_domaine);
CREATE INDEX idx_memoire_annee     ON memoire(annee_academique);
CREATE INDEX idx_memoire_niveau    ON memoire(niveau);
CREATE INDEX idx_memoire_empreinte ON memoire(empreinte_sha256);
CREATE INDEX idx_memoire_encadreur ON memoire(id_encadreur);

-- Les triggers de l'index plein texte ont disparu avec l'ancienne table : on les recrée.
CREATE TRIGGER memoire_fts_ai AFTER INSERT ON memoire BEGIN
    INSERT INTO memoire_fts (rowid, titre, resume, mots_cles)
    VALUES (new.id, new.titre, new.resume, new.mots_cles);
END;

CREATE TRIGGER memoire_fts_ad AFTER DELETE ON memoire BEGIN
    INSERT INTO memoire_fts (memoire_fts, rowid, titre, resume, mots_cles)
    VALUES ('delete', old.id, old.titre, old.resume, old.mots_cles);
END;

CREATE TRIGGER memoire_fts_au AFTER UPDATE OF titre, resume, mots_cles ON memoire BEGIN
    INSERT INTO memoire_fts (memoire_fts, rowid, titre, resume, mots_cles)
    VALUES ('delete', old.id, old.titre, old.resume, old.mots_cles);
    INSERT INTO memoire_fts (rowid, titre, resume, mots_cles)
    VALUES (new.id, new.titre, new.resume, new.mots_cles);
END;

-- Soutenance planifiée par la scolarité, puis tenue (PV saisi).
CREATE TABLE soutenance (
    id_memoire         INTEGER PRIMARY KEY REFERENCES memoire(id) ON DELETE CASCADE,
    date_heure         TEXT NOT NULL,
    salle              TEXT NOT NULL,
    statut             TEXT NOT NULL DEFAULT 'PLANIFIEE' CHECK (statut IN ('PLANIFIEE','TENUE')),
    observations       TEXT,
    id_planificateur   INTEGER REFERENCES utilisateur(id),
    date_planification TEXT NOT NULL
);
CREATE INDEX idx_soutenance_date ON soutenance(date_heure);

-- Composition du jury (l'encadreur en fait toujours partie).
CREATE TABLE jury_membre (
    id_memoire    INTEGER NOT NULL REFERENCES soutenance(id_memoire) ON DELETE CASCADE,
    id_enseignant INTEGER NOT NULL REFERENCES utilisateur(id),
    role          TEXT NOT NULL CHECK (role IN ('PRESIDENT','EXAMINATEUR','ENCADREUR')),
    PRIMARY KEY (id_memoire, id_enseignant)
);
CREATE INDEX idx_jury_enseignant ON jury_membre(id_enseignant);

-- Paramètres de la scolarité (année courante, campagne de dépôt...).
CREATE TABLE parametre (
    cle    TEXT PRIMARY KEY,
    valeur TEXT
);
INSERT INTO parametre (cle, valeur) VALUES ('depot_ouvert', '1');
