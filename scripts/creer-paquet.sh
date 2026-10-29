#!/usr/bin/env bash
# Crée l'application MémoTDSI installable avec jpackage (fourni par le JDK 21).
#   ./scripts/creer-paquet.sh          -> paquet .deb (Ubuntu/Debian, nécessite fakeroot)
#   ./scripts/creer-paquet.sh app      -> dossier autonome (aucun prérequis)
# Installation du .deb : sudo apt install ./target/paquet/memotdsi_1.0.0_amd64.deb
set -euo pipefail
cd "$(dirname "$0")/.."

TYPE="${1:-deb}"
[ "$TYPE" = "app" ] && TYPE="app-image"

if [ "$TYPE" = "deb" ] && ! command -v fakeroot >/dev/null; then
    echo "Le paquet .deb nécessite fakeroot : sudo apt install fakeroot" >&2
    exit 1
fi

echo "1/3 Compilation et tests..."
mvn -q -B package

echo "2/3 Copie des dépendances..."
rm -rf target/lib target/paquet
mvn -q -B dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib
cp target/memotdsi-1.0.jar target/lib/

echo "3/3 jpackage ($TYPE)..."
jpackage \
    --type "$TYPE" \
    --name memotdsi \
    --app-version 1.0.0 \
    --vendor "Faculté TDSI" \
    --description "MémoTDSI : dépôt, validation et recherche des mémoires de la faculté TDSI" \
    --input target/lib \
    --main-jar memotdsi-1.0.jar \
    --main-class com.tdsi.memotheque.Lanceur \
    --java-options "-Dfile.encoding=UTF-8" \
    --dest target/paquet \
    $( [ "$TYPE" = "deb" ] && echo "--linux-shortcut --linux-menu-group Education --linux-app-category education" )

echo "Terminé :"
ls -1 target/paquet
