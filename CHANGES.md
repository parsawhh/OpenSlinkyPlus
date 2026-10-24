# Changes in this build

This project is **OpenSlinky+ by lallu** (credits: Skid Solutions).

## Root cause of "Slinky could not start" (`NoSuchMethodError: Minecraft.getMinecraft()`)
The original `slinky-srg.jar` / `slinky-official.jar` were NOT remapped: their bytecode still called
MCP names (`getMinecraft`, `gameSettings`, `rotationYaw` ...) and only a `mappings.txt` was added.
On any runtime that is not MCP-named (Forge = SRG, Vanilla/OptiFine/Lunar/Badlion = notch) the first
direct call crashed. `tools/verify_payload.py` finds 303 (srg) / 340 (official) stale references in the
old jars and 0 in the new ones.

## What changed
| File | Change |
|---|---|
| `SlinkyNative/payload/slinky-srg.jar`, `slinky-official.jar`, `slinky-named.jar` | Fully remapped payloads (from the BLC-Slinky work): srg for Forge, notch for Vanilla/OptiFine/Lunar/Badlion. |
| `SlinkyNative/src/PayloadLoader.cpp` | `addURL` is best-effort (JDK 9+ loaders are not URLClassLoaders); classes are defined directly in multiple dependency-aware passes; **new:** `addURL` is only called on real `URLClassLoader`s (calling it on anything else is undefined behaviour in JNI). |
| `SlinkyNative/src/MinecraftDetector.cpp` | Rewritten: checks both `net/minecraft/client/Minecraft` and `ave`, finds the singleton field by type instead of one hard-coded name, waits until the game object exists (Lunar/Badlion load the class early), frees JNI refs while polling, logs loader class, and gives a diagnostic log for unknown naming schemes. |
| `Slinky/src/Main.cpp` | Injects into the JVM that has LWJGL loaded (prefers titles with Minecraft/Lunar/Badlion) instead of the first Java window on the machine. |
| `Slinky/src/Injector.cpp` | Startup monitor timeout 30 s -> 150 s (Bootstrap can legitimately wait 2 min). |
| `build.ps1`, `tools/verify_payload.py` | One-command Windows build; static payload checker. |

## Not changed
`SlinkyClient/` (Java sources) is identical to upstream. Rebuilding it with Gradle only produces the *named* jar.

## Mouse stays free after the server closes the GUI (MMC queue, lobby changes, kicks)
When a server closed the click GUI, vanilla re-grabbed the mouse but the GUI module's tick handler released it again
(the module was still "enabled" for one more tick). Once disabled, `setIngameFocus()` did nothing because the game
already believed it had focus, leaving a visible cursor while the camera still turned.
`GuiModule` now (1) disables itself on the first tick where no OpenSlinky+ screen is open and (2) re-grabs the mouse
whenever the game owns the mouse but the cursor is free (`currentScreen == null`, `inGameHasFocus`, window active).
Applied to the Java source and patched into all three prebuilt jars (verified with the JVM bytecode verifier).

## Branding
Display names, chat prefix, GUI title, console banner/title/status messages, window title, READMEs and the output exe
(`OpenSlinkyPlus.exe`) now say OpenSlinky+ / lallu, with credits to Skid Solutions. Technical identifiers (packages,
`SlinkyNative.dll`, resource paths, `/slinky` command, config file names, log file names) are unchanged on purpose.
The Discord links were removed. `assets/` images and the `docs.slinky.gg` help links inside the GUI metadata are untouched.

## Aim assist with auto-click macros (mouse "fire key" at ~20 cps) and Hit Select
Aim assist's "require mouse pressed" gate read `Mouse.isButtonDown` once per tick. A macro presses the button for only a
few milliseconds per click, so most ticks saw "up", which cleared the target and restarted the 1-2 tick re-engage delay
every time. New helper `ClickTracker` samples the buttons every frame/event and keeps the gate on for 180 ms after the last
press. Clicks that Hit Select cancels (with or without fake swing) are reported to it as clicks, so aim assist keeps working
while Hit Select pauses your hits. Changed: `AimAssistModule` (gate + per-event sampling), `HitSelectModule` (one call when it
cancels an attack), new `ClickTracker`. Patched into all three prebuilt jars and verified with the JVM verifier.
