-- Journal infalsifiable : chaque ligne porte HMAC-SHA256(clé secrète, hmac précédent || contenu).
-- Les lignes antérieures à cette migration restent sans hmac (signalées « non protégées »).
ALTER TABLE journal_activite ADD COLUMN hmac TEXT;
