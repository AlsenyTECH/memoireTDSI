# MémoTDSI

Plateforme de gestion des mémoires de la L1 au M2 de la faculté TDSI
(Transmission de Données et Sécurité de l'Information).

Java 21 · JavaFX 21 · SQLite · Maven · Architecture MVC + Service + DAO

## Lancer le projet

Dans Eclipse : clic droit sur le projet > Run As > Maven build... > Goals : `javafx:run`

Ou en ligne de commande : `mvn javafx:run`

## Récupérer le projet (coéquipiers)

1. `File > Import > Git > Projects from Git (with smart import) > Clone URI`
2. Vérifier que le JDK 21 est utilisé (Preferences > Java > Installed JREs)
3. Clic droit sur le projet > Maven > Update Project

## Organisation du code

```
com.tdsi.memotheque
├── model        objets métier + enums
├── dao          interfaces DAO ; dao/impl = implémentations JDBC
├── service      règles métier et contrôle d'accès
├── controller   contrôleurs JavaFX (un par écran)
└── util         connexion BD, session, navigation, validation...
```

Les vues FXML sont dans `src/main/resources/view`, le schéma SQL dans `src/main/resources/db`.

## Propriétaires des fichiers

Chaque fichier contient en commentaire `[Mx]` : seul le membre Mx le modifie.
Voir le document de conception pour le détail des tâches.

## Règles Git

- Jamais de commit direct sur `main` ni `develop`
- Une branche `feature/...` par fonctionnalité, puis Pull Request vers `develop`
- `git pull origin develop` chaque matin
- Messages de commit : `type(module): description` (feat, fix, security, style, refactor, test, docs, chore)
