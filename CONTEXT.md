# OpenSlinky+ Session Context

Exported/updated: 2026-11-16  
Repo (local/phone): `/data/data/com.termux/files/usr/tmp/opencode/osp`  
Repo (VPS): `/root/work/osp` (mirror)

## Objective
- Port real Minecraft 1.8.9 cheat-client modules into OpenSlinky+ as fully configurable ClickGUI modules and push to the user's GitHub.
- Build a script-module framework (client-side script host + MCP server) so modules can be added/hot-reloaded while playing.
- Immediate module targets (user-requested, "best version from Vape/Rise/OpenMyau+"): **LagBreak, WTap, SprintReset, HitSelect, BowAim, AntiFireball, AutoBedDef, AutoLogin, LegitReach, Piercing**.
- User requirements: modules must appear in UI, be toggleable and faithfully configurable (switches/sliders); framework must support **raw MC bridge, packets, draw, files, reflect**; behavior must be instant like original Slinky modules (no manual reload on changes).

## Environment
- Phone: Termux (opencode). VPS: `sirgoonette` (`206.1.100.189`) via SSH; persistent shell `~/.ssh/rsh`, `~/.ssh/rsh-start`. **All builds/tests run on the VPS.**
- Git: `gh` authed as **parsawhh** (repo scope), identity `parsawhh / mermetvahid@gmail.com`. Upstream remote `https://github.com/KrauszAdam16/OpenSlinkyPlus`. Fork checkout on VPS: `/root/work/osp-user` (parsawhh/OpenSlinkyPlus).
- **Status:** repo at `64df15a Initial commit` on `main`; all work is **uncommitted**, no feature branch, **no PR**.
- Build: `bash tools/build-payload.sh` on VPS. `LIBS=/root/work/libs` (gson-2.2.4, rhino-1.7.14, asm-9.7+commons/tree/util), JDK 17.
- Payloads (VPS): `/root/work/payload/slinky-{named,srg,official}.jar`.
- Phone MCP: `~/.local/share/opencode/mcp/slinky_mcp_server.py`, `~/.config/opencode/opencode.jsonc` (`mcp.servers.slinky`), `~/.config/slinky-mcp.json` (host placeholder `192.168.1.100`, port 47900).

## Architecture / Key Decisions
- **MC-free payload:** no `net.minecraft.*` references in new payload code (identical classes for named/srg/official). Verified by `tools/verify_payload.py`. Injector only defines `dev/slinky/`.
- **Rhino:** Rhino 1.7.14 relocated into `dev/slinky/shaded/rhino` (`tools/Relocate.java`). Interpreted (`optimizationLevel -1`).
- **Script host:** `dev/slinky/script/**` (ScriptHost, ScriptModule, ScriptRuntime, ScriptApi, ScriptGame, ScriptManifest, ScriptSetting, ScriptEvents, ScriptEvent, ScriptEngine, ScriptEngines, ScriptValues, ScriptServer, ScriptException). `CoreModules.register` → `ScriptHost.install(manager)`.
- **GUI integration:** MC-free fallbacks in `ModuleCatalog`, `ModuleSettingsLayout`, `InlineModuleSettings` (schema/step fallback), `GuiAppearance`. Script modules are real `Module`s with real settings → ClickGUI toggle + switches/sliders/dropdowns.
- **Fixes:** CME — registry mutation in `onEvent` (tick dispatch). Rhino boolean truthiness — unwrap Boolean/Number/String in `ScriptValues`.
- **Control/MCP:** TCP `0.0.0.0:47900`, scripts `<mcDataDir>/slinky-scripts`.

## Completed (last verified green build before framework WIP edits)
- 3 payload jars injected, 0 unmapped refs, smoke tests: `ScriptHostSmokeTest`, `ScriptServerSmokeTest`, `ScriptModulesSmokeTest`, MCP test OK.
- **`scripts/vape_clutch.js`** — full Vape v4 port: path planner/scoring/triggers, block placement, ladder planner (`planLadder`, `simulateTrajectory`, `findCatchSamples`, `extensionPath`), movement steering via event 21, auto-ladder modes. 33 settings.
- **`scripts/rise_anticheat.js`** — Rise 6.9.5 CheatDetector-inspired (entity-snapshot): SpeedLimit buffer, Flight hover/ascent, Tower impossible-motion, Reach on hurtTime edge; VL counters, cooldown, friend filter, client chat alerts. 13 settings.
- **API:** `placeBlock`, `selectSlot`, `useItem`, `holdingBlock`, `keyDown`, `heldSlot`, `blockAt`, `heldItem`, `hotbar`, `swing`, `attack`, `say` (server), `message` (client-side), `relationshipFlags`/`setFriend`, player/entity maps (vx/vy/vz/hurtTime/fallDistance/AABB), module setting/enabled control, rotation helpers, `lookAt`/delta/camera/claim/noise, `modules()`.
- **GUI:** number-setting step fallback for script modules.

## Module Research (completed)
Sources saved under `/root/work/refs/`:
- `/root/work/refs/vape/` (earlier clutch/ladder/killaura/antibot), `/root/work/refs/rise/` (anticheat checks).
- `/root/work/refs/rise/modules/`, `/root/work/refs/vape/modules/`, `/root/work/refs/myau/modules/` + trees/REPO.txt.

**Rise 6.9.5** (`0botyki/Rise6.9.5`):
- Present: **LagBreak** (372, blink/packet queue via BlinkComponent, dispatch conditions, needs packet queue+cancel/raw), **WTap** (Legit key re-tap / Silent sprint drop, alias "Sprint Reset"), **AntiFireBall** (66, rotate+strafe+swing/C02 or click), **LegitReach/Back Track** (182, tracks real pos from S14/S18, holds packets via BlinkComponent, render; needs packet recv intercept+delay+render), **Piercing** (43, click raycast through blocks).
- Missing: HitSelect, BowAim/BowAimbot, AutoBedDef/BedDefender, AutoLogin, standalone SprintReset.

**Vape v4** (`RSSeeker/Vape-v4@main`, primary):
- Present: **Backtrack** (lag/backtrack, delays inbound packets with latency, server-pos render) and **FakeLag** (Latency/Repel/Dynamic outbound delay modes) — no module named "LagBreak" but lag behaviors covered; **WTap** (chance, release/re-press delays, select-hits/vulnerable gating); **HitSelect** (modes: WTapSprintResetMode / RightClickUseCancelMode, chance); **BowAimbot** (also contains AntiFireball-like behavior per dump; aim speed/angle/silent/movement lock, arrow prediction); **Reach** (range/chance/modes/misplace/vertical/sprint/water checks) — "LegitReach" name absent.
- Missing: AutoBedDef/BedDefender, AutoLogin, Piercing (separate AntiFireball file not found).

**OpenMyau+** (`IamNespola/OpenMyau-Plus@main`):
- Present: **WTap** (delay/duration, blocks moveForward), **SprintReset** (PACKET/LEGIT/SILENT, crit/smart/fast), **HitSelect** (SECOND/CRITICALS/W_TAP, cancels C02), **AntiFireball** (LEGIT/PACKET, silent rot, fov/range), **AutoBedDef** (bed defense placement, silent rotate, slot switch, raytrace), **AutoAuth** (=AutoLogin, chat prompt detect/delay), **LegitReach** (INTAVE/FAKE_PLAYER fake entity), **Reach** (probabilistic range expand), **Piercing** (raytrace/mouseOver override, filters/sort).
- Missing in this repo: **LagBreak** (has LagRange/FakeLag/ServerLag instead), **BowAim/BowAimbot** (best found separately: `4everdies/CrewX` `BowAimbot.java`, downloaded as `BowAimbot_crewx.java`).

## Framework WIP (started, UNBUILT / UNTESTED)
Partially implemented to satisfy "raw bridge, packets, draw, files, reflect + instant hot-reload"; **build not run, API not fully wired**:
- `ScriptEngines.java`: removed `ClassShutter` (full class visibility intended) — needs verification.
- `ScriptEvent.java`: added `getPacket/getPacketName/setPacket/getReceiver` and camera/render getters (`cameraX/Y/Z`, `renderWidth/Height`).
- `ModuleManager.java`: added `replace(Module)` (used for hot-swap).
- `ScriptHost.java`: `reload()` now captures/restores enabled state, binding, hold, and setting values across unload/reload (instant-ish, no reset to defaults).
- `ScriptDraw.java`: new helper class (rect/text/line/outline/project, GL ops) — **not exposed via ScriptApi/ScriptRuntime yet**; render events dispatch but no draw context passed to scripts.
- **Still TODO:** expose raw bridge on `ScriptApi`/`ScriptGame` (`mc()`, `playerEntity()`, `worldEntity()`, raw entity access, `sendPacket`), file IO helpers, reflection helpers, wire `ScriptDraw` into render/gui events, ensure packet objects flow correctly, update smoke tests/docs, rebuild+verify all jars.

## Relevant Files (local, uncommitted)
- Scripts: `scripts/vape_clutch.js`, `scripts/rise_anticheat.js`, `scripts/antichest.js`, `scripts/kill_aura.js`, `scripts/auto_gapple.js`
- Script core: `SlinkyClient/src/main/java/dev/slinky/script/*.java` (incl. new `ScriptDraw.java`, WIP edits)
- GUI patches: `SlinkyClient/src/main/java/dev/slinky/client/gui/GuiAppearance.java`, `.../gui/settings/InlineModuleSettings.java`, `.../gui/settings/ModuleSettingsLayout.java`, `SlinkyClient/src/main/java/dev/slinky/module/catalog/ModuleCatalog.java`, `SlinkyClient/src/main/java/dev/slinky/module/CoreModules.java`
- Manager: `SlinkyClient/src/main/java/dev/slinky/module/ModuleManager.java`
- Tools: `tools/build-payload.sh`, `tools/Relocate.java`, `tools/verify_payload.py`, `tools/ScriptHostSmokeTest.java`, `tools/ScriptServerSmokeTest.java`, `tools/ScriptModulesSmokeTest.java`, `tools/mcp/slinky_mcp_server.py`, `tools/DEPLOY-SCRIPTS.md`

## Next Steps (for delegate)
1. Finish framework: raw bridge + `sendPacket`/packet wrappers, draw context for render/gui events, file IO + reflect helpers; verify ClassShutter removal/unrestricted Rhino is safe for use case; fix hot-reload edge cases (manifest changes adding/removing settings).
2. Rebuild & run `bash tools/build-payload.sh` (inject + verify + smoke tests); update tests for new API behavior.
3. Port modules using best refs: **LagBreak** (Vape Backtrack/FakeLag or Rise LagBreak), **WTap/SprintReset** (Rise WTap + OpenMyau SprintReset), **HitSelect** (OpenMyau/Vape), **BowAim** (CrewX/OpenMyau-style ballistic + Vape), **AntiFireball** (OpenMyau/Vape/Rise), **AutoBedDef** (OpenMyau), **AutoLogin** (OpenMyau AutoAuth), **LegitReach** (Rise/OpenMyau), **Piercing** (OpenMyau/Rise).
4. Ensure each appears in UI, toggleable, fully configurable; commit on `feature/script-modules`, push to `parsawhh/OpenSlinkyPlus`, open PR.

## OneClient 1.8.9 / Fabric injection (done 2026-11-17)
- OneClient = Fabric loader (Knot) + Ornithe; class names stay MCP-style (`net/minecraft/client/Minecraft`) but members are calamus gen2 (`f_33079084`, `m_20213497`).
- A plain Fabric mod cannot install OpenSlinky+ patches: `TransformerEngine` redefines loaded game classes through JVMTI. Fabric provides no native bridge.
- Route used: **tiny java agent + Fabric mod**.
  - `SlinkyNative/payload/slinky-oneclient-agent.jar` (agent jar, ~2 KB): `Premain-Class: dev.slinky.inject.agent.SlinkyAgent` publishes `Instrumentation` in `InstrumentationHolder`; no payload classes (so Knot, not the system loader, loads them).
  - `SlinkyNative/payload/slinky-oneclient.jar`: calamus-remapped payload + `fabric.mod.json` + `dev/slinky/inject/fabric/SlinkyFabricEntrypoint` (`ClientModInitializer`).
  - Install: put the mod jar in `.minecraft/mods` and add `-javaagent:<absolute path>/slinky-oneclient-agent.jar` to the instance JVM arguments. Keep both jars in the same folder.
- `Bootstrap.launchFabric()` waits on a daemon thread for `Minecraft.getMinecraft()`, then `start("calamus", <mod jar>, false)`; `Platform.getClassBytes`/`redefineClass` use `Instrumentation` when present, otherwise fall back to `NativeBridge` (native injector unchanged).
- `addScheduledTask` now goes through the `IThreadListener` interface (`m_78135006`) because calamus only maps the inherited Runnable overload there.
- Logs on the game machine: `<Desktop>/slinky-running.log` (append) and `<Desktop>/slinky-crash.log` (boot failure / uncaught exceptions), see `dev/slinky/inject/LogSink.java`.
- Native detector/rc also know calamus: `MappingType::Calamus`, resource id 104, `IDR_PAYLOAD_CALAMUS` -> `payload/slinky-calamus.jar`.
- Build: `bash tools/build-payload.sh` (all 4 namespaces + agent + mod jar); last run green (0 unmapped refs, all smoke tests OK).
- Remaining: launch OneClient on the Windows box, confirm the two Desktop logs and the ClickGUI modules, then commit/push.


## Ten script-module ports (2026-11-17)
- Ported to `scripts/`: `bow_aim.js`, `auto_bed_def.js`, `auto_login.js`, `legit_reach.js`, `lag_break.js`, `wtap.js`, `sprint_reset.js`, `hitselect.js`, `anti_fireball.js`, `piercing.js` (manifest settings + `tick`/`packet_inbound`/`packet_outbound`/`rotation`/`pre_interact` listeners).
- Fixes this pass:
  - `ScriptGame.setFriend` mask `1` -> `4` (friends = flag 4).
  - `auto_bed_def.js`: `opposite()` now maps `UP`/`DOWN` (support-below now clicks its top face, not `DOWN`); placement uses the destination block + clicked face; waits for applied rotation before clicking; `swing`/animation, slot restore, screen guard, silent move-fix remap on rotation.
  - `legit_reach.js`: S18/S14 fallback uses `ref.get('entityId'|'posX'|'posY'|'posZ')` before `call`.
  - `anti_fireball.js`: uses `p.usingItem` instead of `rawPlayer().call('isUsingItem')`.
  - `sprint_reset.js`: 5-arg C04 `newPacket`; added `ScriptApi.newPacket(String,Object,Object,Object,Object)`.
  - `vape_clutch.js`: `event.type` -> `event.getType()`.
- `tools/ScriptModulesSmokeTest.java`: new tests for the ten modules + `MockEvent`/`MockRef`, expanded `MockApi` (keys, sprint packets, held stack, screen, packets, entity/ref helpers).
- `tools/js-sim.js`: Node-only simulation of the same 18 checks (no JDK needed). `node tools/js-sim.js` -> `js sim: OK`.
- Blocker: `ssh sirgoonette` still fails during kex (`exit=255`), so the VPS payload build / Java smoke test has not been re-run. Sync changed files with `scp` once SSH is back.
