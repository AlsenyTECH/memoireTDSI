# MémoTDSI

Plateforme de dépôt, de validation et de recherche des mémoires de la L1 au M2 de la faculté TDSI
(Transmission de Données et Sécurité de l'Information).

Java 21 · JavaFX 21 · SQLite · Maven · Architecture MVC + Service + DAO · aucune dépendance cryptographique externe (JDK uniquement)

## Fonctionnalités

| Rôle | Ce qu'il peut faire |
|---|---|
| **Étudiant** | S'inscrire (compte activé par l'admin), déposer un mémoire (PDF ≤ 20 Mo), suivre son statut, corriger après un refus, consulter l'historique des versions |
| **Encadreur** | Lire les PDF des mémoires qu'il encadre, valider (avec **signature numérique**) ou refuser avec un motif |
| **Administrateur** | Comptes (activer, désactiver, déverrouiller, réinitialiser un mot de passe, créer un encadreur), domaines, journal d'activité (vérification d'intégrité, export CSV), sauvegarde chiffrée |
| **Tous** | Tableau de bord, recherche plein texte (insensible aux accents, tri par pertinence, filtres, export CSV), fiche détaillée avec vérification de la signature, citation APA / BibTeX, notifications, profil, thème sombre |

Raccourcis : `Ctrl+D` tableau de bord · `Ctrl+F` recherche · `Ctrl+N` notifications · `Ctrl+P` profil · `Ctrl+Q` déconnexion.

## Sécurité

| Menace | Protection |
|---|---|
| Vol de la base | Mots de passe **PBKDF2-HMAC-SHA256, 600 000 itérations**, sel de 16 octets, format versionné (`pbkdf2-sha256$n$hash`) et re-hachage automatique des anciens hashes |
| Vol du dossier `storage/` | PDF **chiffrés AES-256-GCM** (clé dans un coffre PKCS12 hors de la base, nom du fichier en données associées) |
| Fichier modifié ou échangé | Tag GCM + SHA-256 vérifiés à chaque ouverture ; validation impossible si le PDF a changé |
| Falsification d'un mémoire publié | **Signature Ed25519** du validateur sur (id, SHA-256, titre, auteur, année, niveau, validateur, date) ; clé privée chiffrée par son mot de passe ; vérifiée sur la fiche |
| Effacement de traces | **Journal chaîné HMAC-SHA256** + ancre authentifiée : toute ligne modifiée, insérée ou supprimée (même les dernières) est détectée |
| Force brute / énumération | Verrouillage 15 min après 5 échecs, message d'échec unique, temps de réponse constant (PBKDF2 factice), politique de mot de passe |
| Injection SQL | `PreparedStatement` partout, `ORDER BY` issu d'une liste fermée, requêtes plein texte construites mot par mot |
| Injection de formules (CSV) | Cellules commençant par `= + - @` neutralisées à l'export |
| Élévation de privilèges | Rôles vérifiés **dans les services** (pas seulement en masquant des boutons) |
| Poste laissé ouvert | Déconnexion automatique après 15 minutes d'inactivité |
| Perte de données | Sauvegarde **chiffrée AES-256-GCM** (clé PBKDF2), en-tête authentifié, restauration vérifiée dans un dossier neuf (anti zip slip) |
| Plagiat grossier | Refus d'un PDF déjà déposé (même SHA-256) |

## Installation sur Ubuntu

Prérequis : Java 21 (JDK) et Maven. JavaFX, SQLite et JUnit sont téléchargés par Maven.

```bash
sudo apt install -y openjdk-21-jdk maven git
```

Vérifier que Maven utilise Java 21 (la ligne `Java version` doit indiquer 21) :

```bash
mvn -v
```

Récupérer le projet puis lancer les tests :

```bash
git clone https://github.com/AlsenyTECH/memoireTDSI.git && cd memoireTDSI
```

```bash
mvn test
```

## Lancer l'application

```bash
mvn javafx:run
```

**Premier démarrage** : si aucun administrateur n'existe, l'application demande de le créer
(mot de passe d'au moins 12 caractères avec lettres et chiffres).

**Comptes de démonstration** (facultatif) : ils sont créés au démarrage si ces variables existent.

```bash
MEMOTDSI_ADMIN_PASSWORD="AdminDemo2026" MEMOTDSI_ENCADREUR_PASSWORD="EncadreurDemo2026" MEMOTDSI_ETUDIANT1_PASSWORD="Etudiant1Demo2026" MEMOTDSI_ETUDIANT2_PASSWORD="Etudiant2Demo2026" mvn javafx:run
```

Comptes : `admin@memotdsi.local`, `encadreur.demo@memotdsi.local`, `etudiant1.demo@memotdsi.local`, `etudiant2.demo@memotdsi.local`.

Dans Eclipse : clic droit sur le projet > Run As > Maven build... > Goals : `javafx:run`
(variables dans l'onglet *Environment*).

## Application installable (.deb)

```bash
sudo apt install -y fakeroot
```

```bash
./scripts/creer-paquet.sh
```

```bash
sudo apt install ./target/paquet/memotdsi_1.0.0_amd64.deb
```

`./scripts/creer-paquet.sh app` crée une version autonome sans installation (`target/paquet/memotdsi/bin/memotdsi`).
Une fois installée, l'application range ses données dans `~/.local/share/memotdsi` (droits 700).

## Fichiers de données (à sauvegarder, à ne jamais committer)

| Fichier | Contenu |
|---|---|
| `memotdsi.db` | Base SQLite |
| `storage/` | PDF chiffrés (`<uuid>.pdf.enc`) |
| `memotdsi.p12` | Coffre de clés (clé AES des PDF, clé HMAC du journal) — **sans lui, les PDF sont illisibles** |
| `memotdsi.p12.pass` | Mot de passe du coffre, généré au premier lancement si `MEMOTDSI_KEYSTORE_PASSWORD` n'est pas défini |
| `memotdsi.p12.journal-ancre` | Ancre du journal (détection de suppression des dernières lignes) |

La sauvegarde de l'écran *Sauvegarde* (admin) contient tout cela, chiffré.

## Organisation du code

```
com.tdsi.memotheque
├── model        objets métier + enums
├── dao          interfaces DAO ; dao/impl = implémentations JDBC
├── service      règles métier, contrôle d'accès, cryptographie (signature, fichiers, sauvegarde)
├── controller   contrôleurs JavaFX (un par écran)
└── util         connexion BD, migrations, coffre de clés, crypto, session, navigation, validation...
```

Les vues FXML sont dans `src/main/resources/view`, le schéma SQL dans `src/main/resources/db`.

**Évolutions du schéma** : ne pas modifier `schema.sql` ; ajouter un script `db/migrations/V{n}__nom.sql`
et l'inscrire dans `Migrations.SCRIPTS`. Il est appliqué une seule fois, dans une transaction,
y compris sur les bases déjà installées.

## Tests

`mvn test` lance 165 tests JUnit 5 sur des bases SQLite temporaires (la vraie base n'est jamais touchée),
dont les tests de sécurité : injection SQL et FTS, faux PDF, fichier de plus de 20 Mo, verrouillage,
rôles, refus sans motif, falsification du journal, des PDF chiffrés, des signatures et des sauvegardes.
GitHub Actions les relance à chaque push et Pull Request (`.github/workflows/ci.yml`).

## Propriétaires des fichiers

Chaque fichier contient en commentaire `[Mx]` : seul le membre Mx le modifie.
Voir le document de conception pour le détail des tâches.

## Règles Git

- Jamais de commit direct sur `main` ni `develop`
- Une branche `feature/...` par fonctionnalité, puis Pull Request vers `develop`
- `git pull origin develop` chaque matin
- Messages de commit : `type(module): description` (feat, fix, security, style, refactor, test, docs, chore)
