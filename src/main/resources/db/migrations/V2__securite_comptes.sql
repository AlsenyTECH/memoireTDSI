-- Verrouillage temporaire (date du verrouillage) et changement de mot de passe imposé
-- après une réinitialisation par l'administrateur.
ALTER TABLE utilisateur ADD COLUMN date_verrouillage TEXT;
ALTER TABLE utilisateur ADD COLUMN doit_changer_mdp INTEGER NOT NULL DEFAULT 0;
