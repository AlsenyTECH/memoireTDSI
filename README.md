# MémoTDSI

Plateforme de dépôt, de validation et de recherche des mémoires de la L1 au M2 de la faculté TDSI
(Transmission de Données et Sécurité de l'Information).

Java 21 · JavaFX 21 · SQLite · Maven · Architecture MVC + Service + DAO · aucune dépendance cryptographique externe (JDK uniquement)

## Circuit d'un mémoire

Inspiré des pratiques des universités (LMD) et des archives ouvertes de mémoires (DUMAS, theses.fr) :
chacun ne fait que ce qui relève de son rôle.

```
Étudiant            Encadreur              Scolarité (admin)                    Jury / Scolarité
dépose le PDF  ──►  avis favorable  ──►   planifie la soutenance  ──►         PV : note /20
(diffusion,         ou corrections         (date, salle, président,             → mention calculée
 consentement)      demandées (motif)      examinateur(s), encadreur d'office)  → admis : publié + PV signé
      ▲                    │                                                    → ajourné : retour à l'étudiant
      └──── corrige ◄──────┘◄───────────────────────────────────────────────────────────┘
```

| État | Signification |
|---|---|
| Soumis à l'encadreur | Déposé, en attente de l'avis de l'encadreur (modifiable par l'étudiant) |
| Corrections demandées | Refus motivé de l'encadreur, ou ajournement en soutenance (note < 10) |
| Autorisé à soutenir | Avis favorable : la scolarité planifie la soutenance |
| Publié | PV enregistré : note, mention et date de soutenance fixées, PV signé (Ed25519) |

**L'étudiant ne fixe ni l'année, ni le niveau, ni la date de soutenance, ni la mention** :
l'année vient de la campagne ouverte par la scolarité, le niveau de son inscription,
la date et le jury de la planification, la mention de la note du jury.

Barème des mentions : < 10 ajourné · 10–12 Passable · 12–14 Assez bien · 14–16 Bien · ≥ 16 Très bien.

Diffusion (choisie par l'étudiant au dépôt, ajustable au PV) : **libre** (notice et PDF pour tous) ·
**restreinte** (notice visible, PDF réservé aux enseignants et à l'administration) ·
**confidentielle** (invisible au catalogue jusqu'à la fin d'un embargo de 5 ans maximum).
L'auteur, l'encadreur et les membres du jury gardent toujours l'accès.

## Fonctionnalités par rôle

| Rôle | Ce qu'il peut faire |
|---|---|
| **Étudiant** | S'inscrire (compte activé par l'admin) ou être inscrit par la scolarité ; déposer pendant la campagne (PDF ≤ 20 Mo, niveau de diffusion, déclaration d'auteur) ; suivre son mémoire (état, soutenance, résultat, prochaine étape) ; corriger après une demande de corrections ; historique des versions ; **attestation de dépôt** imprimable (avec l'empreinte SHA-256) |
| **Enseignant (encadreur)** | **Mémoires à évaluer** : lire le PDF, avis favorable ou corrections demandées ; **Mes encadrements** : suivi de ses étudiants à toutes les étapes ; **Mes soutenances (jury)** : son rôle (président, examinateur, encadreur) et sa convocation |
| **Scolarité (administrateur)** | **Campagne de dépôt** (année en cours, ouverture, date limite) ; **Soutenances** : planification avec jury et contrôle des conflits de salle et de membres, annulation, **saisie du PV** (note → mention, diffusion, signature), convocations et PV imprimables ; **Suivi des mémoires** ; comptes (activation, verrouillage, mot de passe temporaire, création d'encadreurs, **inscription des étudiants par CSV**) ; domaines ; journal d'activité vérifiable ; sauvegarde chiffrée |
| **Tous** | Tableau de bord avec **« À faire »** selon le rôle et prochaines soutenances ; recherche plein texte (insensible aux accents, tri par pertinence, filtres, export CSV) ; fiche détaillée (soutenance, résultat, diffusion, vérification de la signature) ; citation APA / BibTeX ; notifications à chaque étape ; profil ; thème sombre |

Inscription des étudiants par la scolarité : fichier CSV UTF-8, une ligne par étudiant
(séparateur `;` ou `,`, en-tête facultatif). Un étudiant déjà inscrit (même matricule et même e-mail)
est **réinscrit** à son nouveau niveau ; un nouveau reçoit un mot de passe temporaire à changer à la première connexion.

```
matricule;nom;prenom;email;niveau
TDSI-2026-001;Sow;Awa;awa.sow@exemple.sn;M1
```

Raccourcis : `Ctrl+D` tableau de bord · `Ctrl+F` recherche · `Ctrl+N` notifications · `Ctrl+P` profil · `Ctrl+Q` déconnexion.

## Sécurité

| Menace | Protection |
|---|---|
| Vol de la base | Mots de passe **PBKDF2-HMAC-SHA256, 600 000 itérations**, sel de 16 octets, format versionné (`pbkdf2-sha256$n$hash`) et re-hachage automatique des anciens hashes |
| Vol du dossier `storage/` | PDF **chiffrés AES-256-GCM** (clé dans un coffre PKCS12 hors de la base, nom du fichier en données associées) |
| Fichier modifié ou échangé | Tag GCM + SHA-256 vérifiés à chaque ouverture ; validation impossible si le PDF a changé |
| Falsification d'un résultat | **PV signé en Ed25519** par la scolarité sur (id, SHA-256 du PDF, titre, auteur, année, niveau, mention, note, date de soutenance, signataire, date de publication) ; clé privée chiffrée par son mot de passe ; vérifiée sur la fiche |
| Accès à un mémoire confidentiel | Niveau de diffusion et embargo contrôlés **dans les services** (catalogue, ouverture et téléchargement du PDF) |
| Effacement de traces | **Journal chaîné HMAC-SHA256** + ancre authentifiée : toute ligne modifiée, insérée ou supprimée (même les dernières) est détectée |
| Force brute / énumération | Verrouillage 15 min après 5 échecs, message d'échec unique, temps de réponse constant (PBKDF2 factice), politique de mot de passe |
| Injection SQL | `PreparedStatement` partout, `ORDER BY` issu d'une liste fermée, requêtes plein texte construites mot par mot |
| Injection de formules (CSV) | Cellules commençant par `= + - @` neutralisées à l'export |
| Élévation de privilèges | Rôles vérifiés **dans les services** (pas seulement en masquant des boutons) |
| Poste laissé ouvert | Déconnexion automatique après 15 minutes d'inactivité |
| Perte de données | Sauvegarde **chiffrée AES-256-GCM** (clé PBKDF2), en-tête authentifié, restauration vérifiée dans un dossier neuf (anti zip slip) |
| Plagiat grossier | Refus d'un PDF déjà déposé (même SHA-256) |

## Installation sur Ubuntu

Prérequis : Java 21 (JDK). Maven est facultatif : `./mvnw` le télécharge (remplacer `mvn` par `./mvnw`).
JavaFX, SQLite et JUnit sont téléchargés automatiquement.

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

## Installation sur Windows

Seuls Java 21 et Git sont nécessaires : le Maven Wrapper (`mvnw.cmd`) télécharge Maven tout seul.
Dans PowerShell :

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

```powershell
winget install Git.Git
```

Fermer puis rouvrir PowerShell, puis :

```powershell
git clone https://github.com/AlsenyTECH/memoireTDSI.git; cd memoireTDSI
```

```powershell
.\mvnw.cmd test
```

```powershell
.\mvnw.cmd javafx:run
```

Comptes de démonstration sous PowerShell (facultatif) :

```powershell
$env:MEMOTDSI_ADMIN_PASSWORD="AdminDemo2026"; $env:MEMOTDSI_ENCADREUR_PASSWORD="EncadreurDemo2026"; $env:MEMOTDSI_ETUDIANT1_PASSWORD="Etudiant1Demo2026"; $env:MEMOTDSI_ETUDIANT2_PASSWORD="Etudiant2Demo2026"; .\mvnw.cmd javafx:run
```

Application autonome Windows (`target\paquet\memotdsi\memotdsi.exe`, données dans `%LOCALAPPDATA%\MemoTDSI`) :

```powershell
powershell -ExecutionPolicy Bypass -File scripts\creer-paquet.ps1
```

## Interface

Barre latérale à icônes organisée par rôle, barre du haut avec le titre de l'écran, la cloche des
notifications (badge du nombre non lu), le thème clair / sombre, le profil et la déconnexion.
États des mémoires en pastilles colorées, tableau de bord avec bannière d'accueil.

- **Illustrations** (`src/main/resources/images/`) : campus au crépuscule face à l'Atlantique,
  dessin original généré pour le projet (aucune photo sous droits).
- **Mettre une vraie photo de l'université** : déposer `campus.jpg` (ou `.png`) dans le dossier
  `images/` du dossier de données (à côté de `memotdsi.db` avec `mvn javafx:run`) ; elle remplace
  l'illustration sur l'écran de connexion et la bannière du tableau de bord.
- **Icônes** : Material Design Icons (Google, licence Apache 2.0), dessinées en vectoriel par
  JavaFX (`util/Icone.java`), sans dépendance supplémentaire.

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
