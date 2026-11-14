#!/usr/bin/env bash
# Crea LottoInJava-1.0-x86_64.AppImage (pensato per EndeavourOS / Arch)
set -euo pipefail
cd "$(dirname "$0")"

APP=LottoInJava
VERSION=1.0
JAR="lottoinjava-desktop-${VERSION}-all.jar"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Manca '$1'. $2"; exit 1; }; }
need mvn      "Installa con: sudo pacman -S maven"
need jpackage "Serve un JDK completo (non solo il JRE): sudo pacman -S jdk21-openjdk"
need curl     "Installa con: sudo pacman -S curl"

echo "==> 1/4 Compilo con Maven"
mvn -q clean package

echo "==> 2/4 Creo l'app con il suo Java incluso (jpackage)"
rm -rf build && mkdir -p build/input
cp "target/${JAR}" build/input/
jpackage --type app-image --name "$APP" --dest build \
  --input build/input --main-jar "$JAR" \
  --main-class com.example.lottoinjava.desktop.Launcher \
  --add-modules java.base,java.desktop,java.logging,java.xml,java.prefs,java.net.http,jdk.unsupported,java.naming,jdk.crypto.ec,jdk.localedata \
  --icon packaging/lottoinjava.png

echo "==> 3/4 Preparo la cartella AppDir"
APPDIR="build/${APP}.AppDir"
mkdir -p "$APPDIR"
mkdir -p "$APPDIR/usr"
cp -r "build/${APP}/." "$APPDIR/usr/"
cp packaging/LottoInJava.desktop "$APPDIR/${APP}.desktop"
cp packaging/lottoinjava.png "$APPDIR/lottoinjava.png"
ln -sf lottoinjava.png "$APPDIR/.DirIcon"
cat > "$APPDIR/AppRun" <<'RUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/LottoInJava" "$@"
RUN
chmod +x "$APPDIR/AppRun"

echo "==> 4/4 Creo l'AppImage"
TOOL="build/appimagetool-x86_64.AppImage"
if [ ! -x "$TOOL" ]; then
  curl -L -o "$TOOL" "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
  chmod +x "$TOOL"
fi
ARCH=x86_64 "$TOOL" --appimage-extract-and-run "$APPDIR" "${APP}-${VERSION}-x86_64.AppImage"

echo
echo "Fatto: $(pwd)/${APP}-${VERSION}-x86_64.AppImage"
echo "Avvio:  chmod +x ${APP}-${VERSION}-x86_64.AppImage && ./${APP}-${VERSION}-x86_64.AppImage"
