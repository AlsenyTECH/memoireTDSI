-- Index plein texte (FTS5) sur titre, résumé et mots-clés, insensible à la casse et aux accents.
-- Les triggers tiennent l'index à jour quelle que soit la façon dont la table memoire est modifiée.
CREATE VIRTUAL TABLE memoire_fts USING fts5(
    titre, resume, mots_cles,
    content = 'memoire', content_rowid = 'id',
    tokenize = 'unicode61 remove_diacritics 2'
);

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

-- Indexe les mémoires déjà présents.
INSERT INTO memoire_fts (memoire_fts) VALUES ('rebuild');
