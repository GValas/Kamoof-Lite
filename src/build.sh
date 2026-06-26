#!/usr/bin/env bash
# Build de KamoofLite -> plugins/KamoofLite.jar (version Linux / devcontainer).
# Lancer : bash dev/src/build.sh   (depuis n'importe ou)
# Necessite JDK 25 (l'image du devcontainer fournit eclipse-temurin:25-jdk).
set -euo pipefail

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # .../dev/src

# Racine du serveur Paper : on remonte jusqu'a trouver le paper-*.jar
ROOT="$SRC_DIR"
while [ "$ROOT" != "/" ] && ! ls "$ROOT"/paper-*.jar >/dev/null 2>&1; do
  ROOT="$(dirname "$ROOT")"
done
PAPER_JAR="$(ls "$ROOT"/paper-*.jar 2>/dev/null | head -n1 || true)"
[ -n "$PAPER_JAR" ] || { echo "paper-*.jar introuvable en remontant depuis $SRC_DIR"; exit 1; }

LIB_DIR="$ROOT/libraries"
PLUGINS_DIR="$ROOT/plugins"
OUT_JAR="$PLUGINS_DIR/KamoofLite.jar"
BUILD_DIR="$SRC_DIR/build"

echo "Racine serveur : $ROOT"
echo "Paper jar      : $PAPER_JAR"

# Classpath = paper + tous les jars de libraries/
CP="$PAPER_JAR"
if [ -d "$LIB_DIR" ]; then
  while IFS= read -r -d '' j; do CP="$CP:$j"; done < <(find "$LIB_DIR" -name '*.jar' -print0)
fi

# Compilation
rm -rf "$BUILD_DIR"; mkdir -p "$BUILD_DIR"
echo "Compilation ($(javac -version 2>&1))..."
javac -cp "$CP" -d "$BUILD_DIR" "$SRC_DIR/kamoof/KamoofLite.java"

# plugin.yml a la racine du jar
cp "$SRC_DIR/plugin.yml" "$BUILD_DIR/plugin.yml"

# Backup de l'ancien jar
mkdir -p "$PLUGINS_DIR"
if [ -f "$OUT_JAR" ]; then
  cp "$OUT_JAR" "$OUT_JAR.$(date +%Y%m%d-%H%M%S).bak"
  echo "Backup de l'ancien jar effectue."
fi

# Packaging
jar cf "$OUT_JAR" -C "$BUILD_DIR" plugin.yml -C "$BUILD_DIR" kamoof
echo "OK -> $OUT_JAR"
echo "Tape 'restart' dans la console du serveur pour recharger."
