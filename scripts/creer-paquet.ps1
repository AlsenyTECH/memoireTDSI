# Crée l'application MémoTDSI autonome pour Windows avec jpackage (fourni par le JDK 21).
#   powershell -ExecutionPolicy Bypass -File scripts\creer-paquet.ps1
# Résultat : target\paquet\memotdsi\memotdsi.exe (aucune installation nécessaire).
# Un installateur .exe/.msi demanderait en plus WiX Toolset ; l'application autonome suffit.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "1/3 Compilation et tests..."
.\mvnw.cmd -q -B package
if ($LASTEXITCODE -ne 0) { throw "Échec de la compilation ou des tests." }

Write-Host "2/3 Copie des dépendances..."
Remove-Item -Recurse -Force target\lib, target\paquet -ErrorAction SilentlyContinue
.\mvnw.cmd -q -B dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=target/lib"
if ($LASTEXITCODE -ne 0) { throw "Échec de la copie des dépendances." }
Copy-Item target\memotdsi-1.0.jar target\lib\

Write-Host "3/3 jpackage (application autonome)..."
jpackage --type app-image --name memotdsi --app-version 1.0.0 `
    --vendor "Faculte TDSI" `
    --description "MemoTDSI : depot, validation et recherche des memoires" `
    --input target\lib --main-jar memotdsi-1.0.jar `
    --main-class com.tdsi.memotheque.Lanceur `
    --java-options "-Dfile.encoding=UTF-8" `
    --dest target\paquet
if ($LASTEXITCODE -ne 0) { throw "Échec de jpackage." }

Write-Host "Terminé : target\paquet\memotdsi\memotdsi.exe"
