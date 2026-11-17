#!/usr/bin/env bash
# Rebuilds the four payload jars (named, srg, official, calamus) with the script host, the
# refreshed inject package and the bundled Rhino runtime.
#
#   bash tools/build-payload.sh
#
# Environment:
#   LIBS  directory holding gson-2.2.4.jar, rhino-1.7.14.jar and asm-9.7*.jar (default /root/work/libs)
#   OUT   scratch directory (default /root/work/build)
#
# The script host never references net.minecraft.*, so the very same class files are valid for
# every namespace. The inject package does touch Minecraft, so it is compiled once (named) and
# remapped per namespace with tools/RemapPayload.java (srg, official) or whole-jar (calamus).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBS="${LIBS:-/root/work/libs}"
OUT="${OUT:-/root/work/build}"
PAYLOAD="$ROOT/SlinkyNative/payload"

CP="$PAYLOAD/slinky-named.jar:$LIBS/gson-2.2.4.jar:$LIBS/rhino-1.7.14.jar"
ASM="$LIBS/asm-9.7.jar:$LIBS/asm-commons-9.7.jar:$LIBS/asm-tree-9.7.jar:$LIBS/asm-util-9.7.jar"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/stubs" "$OUT/payload"

echo "[1/5] compiling"
cd "$ROOT/SlinkyClient/src/main/java"
javac --release 8 -nowarn -cp "$CP" -d "$OUT/classes" \
   dev/slinky/script/*.java \
   dev/slinky/module/catalog/ModuleCatalog.java \
   dev/slinky/module/CoreModules.java \
   dev/slinky/client/gui/settings/ModuleSettingsLayout.java \
   dev/slinky/client/gui/settings/InlineModuleSettings.java \
   dev/slinky/client/gui/GuiAppearance.java

javac --release 8 -nowarn -d "$OUT/stubs" \
   "$ROOT/tools/stubs/net/minecraft/client/Minecraft.java" \
   "$ROOT/tools/stubs/net/minecraft/util/IThreadListener.java" \
   "$ROOT/tools/stubs/net/fabricmc/api/ClientModInitializer.java" \
   "$ROOT/tools/stubs/com/google/common/util/concurrent/ListenableFuture.java"

javac --release 8 -nowarn -cp "$OUT/stubs:$CP" -d "$OUT/classes" \
   dev/slinky/inject/*.java \
   dev/slinky/inject/transform/*.java

echo "[2/5] relocating rhino"
java -cp "$ASM" "$ROOT/tools/Relocate.java" "$LIBS/rhino-1.7.14.jar" "$OUT/classes" "$OUT/payload"

echo "[2b/5] injecting the named payload"
(cd "$OUT/payload" && jar uf "$PAYLOAD/slinky-named.jar" dev)

echo "[2c/5] remapping the inject classes for srg / official"
(cd "$OUT/classes" && jar cf "$OUT/inject-named.jar" dev/slinky/inject)
for j in srg official; do
   java -cp "$ASM" "$ROOT/tools/RemapPayload.java" "$OUT/inject-named.jar" \
      "$ROOT/SlinkyClient/mappings/$j.txt" "$OUT/inject-$j.jar"
   rm -rf "$OUT/payload-$j" && mkdir -p "$OUT/payload-$j"
   (cd "$OUT/payload-$j" && jar xf "$OUT/inject-$j.jar" dev)
   (cd "$OUT/payload-$j" && jar uf "$PAYLOAD/slinky-$j.jar" dev)
done

echo "[2d/5] building the calamus (OneClient/Ornithe) payload"
java -cp "$ASM" "$ROOT/tools/RemapPayload.java" "$PAYLOAD/slinky-named.jar" "$ROOT/SlinkyClient/mappings/calamus.txt" "$PAYLOAD/slinky-calamus.jar"

echo "[2e/5] packaging the OneClient (Fabric + java agent) artifacts"
mkdir -p "$OUT/agent"
javac --release 8 -nowarn -d "$OUT/agent" \
   dev/slinky/inject/agent/SlinkyAgent.java \
   dev/slinky/inject/agent/InstrumentationHolder.java
printf 'Manifest-Version: 1.0\nPremain-Class: dev.slinky.inject.agent.SlinkyAgent\nAgent-Class: dev.slinky.inject.agent.SlinkyAgent\nCan-Redefine-Classes: true\nCan-Retransform-Classes: true\n\n' > "$OUT/agent/MANIFEST.MF"
(cd "$OUT/agent" && jar cfm "$PAYLOAD/slinky-oneclient-agent.jar" MANIFEST.MF dev)

mkdir -p "$OUT/fabric"
javac --release 8 -nowarn -cp "$OUT/stubs:$CP" -d "$OUT/fabric" \
   dev/slinky/inject/fabric/SlinkyFabricEntrypoint.java
cp "$ROOT/tools/oneclient/fabric.mod.json" "$OUT/fabric/"
cp "$PAYLOAD/slinky-calamus.jar" "$PAYLOAD/slinky-oneclient.jar"
(cd "$OUT/fabric" && jar uf "$PAYLOAD/slinky-oneclient.jar" fabric.mod.json dev)

echo "[3/5] verifying"
cd "$ROOT"
for j in named srg official calamus; do
   case $j in named) m=official.txt ;; srg) m=srg.txt ;; official) m=official.txt ;; calamus) m=calamus.txt ;; esac
   python3 tools/verify_payload.py "SlinkyNative/payload/slinky-$j.jar" "$j" "SlinkyClient/mappings/$m"
done

echo "[5/5] smoke tests"
CP="$OUT/payload:$PAYLOAD/slinky-named.jar:$LIBS/gson-2.2.4.jar"
mkdir -p "$OUT/tests"
javac --release 8 -nowarn -cp "$CP" -d "$OUT/tests" tools/ScriptHostSmokeTest.java tools/ScriptServerSmokeTest.java
java -cp "$OUT/tests:$CP" ScriptHostSmokeTest | tail -3
java -cp "$OUT/tests:$CP" dev.slinky.script.ScriptServerSmokeTest | tail -3
mkdir -p "$OUT/modtests"
javac --release 8 -nowarn -cp "$CP" -d "$OUT/modtests" tools/ScriptModulesSmokeTest.java
java -cp "$OUT/modtests:$CP" dev.slinky.script.ScriptModulesSmokeTest "$ROOT/scripts" | tail -30

ls -la "$PAYLOAD"
