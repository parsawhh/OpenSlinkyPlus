#!/usr/bin/env bash
# Rebuilds the three payload jars with the script host and the bundled Rhino runtime.
#
#   bash tools/build-payload.sh
#
# Environment:
#   LIBS  directory holding gson-2.2.4.jar, rhino-1.7.14.jar and asm-9.7*.jar (default /root/work/libs)
#   OUT   scratch directory (default /root/work/build)
#
# Everything stays in the named namespace: the script host never references net.minecraft.*, so the very
# same class files are valid for the named, srg and official payloads.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBS="${LIBS:-/root/work/libs}"
OUT="${OUT:-/root/work/build}"
PAYLOAD="$ROOT/SlinkyNative/payload"

CP="$PAYLOAD/slinky-named.jar:$LIBS/gson-2.2.4.jar:$LIBS/rhino-1.7.14.jar"
ASM="$LIBS/asm-9.7.jar:$LIBS/asm-commons-9.7.jar:$LIBS/asm-tree-9.7.jar:$LIBS/asm-util-9.7.jar"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/payload"

echo "[1/5] compiling"
cd "$ROOT/SlinkyClient/src/main/java"
javac --release 8 -nowarn -cp "$CP" -d "$OUT/classes" \
   dev/slinky/script/*.java \
   dev/slinky/module/catalog/ModuleCatalog.java \
   dev/slinky/module/CoreModules.java \
   dev/slinky/client/gui/settings/ModuleSettingsLayout.java \
   dev/slinky/client/gui/settings/InlineModuleSettings.java \
   dev/slinky/client/gui/GuiAppearance.java

echo "[2/5] relocating rhino"
java -cp "$ASM" "$ROOT/tools/Relocate.java" "$LIBS/rhino-1.7.14.jar" "$OUT/classes" "$OUT/payload"

echo "[3/5] injecting"
for j in named srg official; do
   (cd "$OUT/payload" && jar uf "$PAYLOAD/slinky-$j.jar" dev)
done

echo "[4/5] verifying"
cd "$ROOT"
for j in named srg official; do
   case $j in named) m=official.txt ;; *) m=$j.txt ;; esac
   python3 tools/verify_payload.py "SlinkyNative/payload/slinky-$j.jar" "$j" "SlinkyClient/mappings/$m"
done

echo "[5/5] smoke tests"
CP="$OUT/payload:$PAYLOAD/slinky-named.jar:$LIBS/gson-2.2.4.jar"
mkdir -p "$OUT/tests"
javac --release 8 -nowarn -cp "$CP" -d "$OUT/tests" tools/ScriptHostSmokeTest.java tools/ScriptServerSmokeTest.java
java -cp "$OUT/tests:$CP" ScriptHostSmokeTest | tail -3
java -cp "$OUT/tests:$CP" dev.slinky.script.ScriptServerSmokeTest | tail -3

ls -la "$PAYLOAD"
