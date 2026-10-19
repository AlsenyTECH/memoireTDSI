-- [M6] Données de départ (rejouées à chaque démarrage : INSERT OR IGNORE évite les doublons).
-- À compléter par M5 : comptes de test et mémoires fictifs.
-- Le compte admin par défaut est créé par le code (AuthService, M2) car son mot de passe doit être haché.

INSERT OR IGNORE INTO domaine (nom) VALUES
    ('Réseaux et télécommunications'),
    ('Transmission de données'),
    ('Cybersécurité'),
    ('Cryptographie'),
    ('Sécurité des applications web'),
    ('Internet des objets (IoT)'),
    ('Cloud et virtualisation'),
    ('Forensique et investigation numérique'),
    ('Intelligence artificielle appliquée à la sécurité'),
    ('Systèmes embarqués');
