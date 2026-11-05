-- V10 : registre des documents officiels émis (convocations, PV, attestations), signés par
-- la clé Ed25519 de l'établissement et vérifiables par QR code ; double authentification TOTP.
CREATE TABLE document_emis (
    code           TEXT PRIMARY KEY,               -- 16 caractères Base32 aléatoires (80 bits)
    type           TEXT NOT NULL,                  -- CONVOCATION, PV, ATTESTATION_DEPOT
    id_reference   INTEGER NOT NULL,               -- id du mémoire concerné
    sujet          TEXT NOT NULL,                  -- destinataire / titulaire
    contenu        TEXT NOT NULL,                  -- charge utile signée (texte canonique)
    signature      TEXT NOT NULL,                  -- Ed25519, Base64url
    empreinte_etat TEXT NOT NULL,                  -- état du dossier au moment de l'émission
    date_emission  TEXT NOT NULL,
    id_emetteur    INTEGER REFERENCES utilisateur(id) ON DELETE SET NULL,
    revoque        INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_document_emis_ref ON document_emis(type, id_reference, sujet);

CREATE TABLE double_facteur (
    id_utilisateur  INTEGER PRIMARY KEY REFERENCES utilisateur(id) ON DELETE CASCADE,
    secret_chiffre  TEXT NOT NULL,                 -- AES-256-GCM (clé du coffre), Base64
    dernier_pas     INTEGER NOT NULL DEFAULT -1,   -- anti-rejeu : dernier pas de 30 s accepté
    date_activation TEXT NOT NULL
);
