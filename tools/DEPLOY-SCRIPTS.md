# Script modules for OpenSlinky+

This build adds a runtime script host to the client: modules written in JavaScript are loaded, hot
reloaded and driven from an MCP server while the game is running.

## What is in the payload

* `dev/slinky/script/**` - the script host (manifest parser, module wrapper, script api, file watcher,
  control channel).
* `dev/slinky/shaded/rhino/**` - the Rhino JavaScript engine, relocated so the injector can define it.
  Rhino 1.7.14 works on Java 8 through 17.
* Patched fallbacks so runtime modules do not break the recovered ui: `ModuleCatalog`,
  `ModuleSettingsLayout`, `InlineModuleSettings`, `GuiAppearance`.

All of it is free of `net.minecraft.*` references, so the identical class files are valid for the
named, srg and official payload jars. `tools/verify_payload.py` reports 0 unmapped references for all
three.

## Windows side

1. Copy the payload jar that matches your client over the one next to the injector:
   * Lunar / Badlion / vanilla (notch names): `slinky-official.jar`
   * Forge 1.8.9: `slinky-srg.jar`
   * MCP named client: `slinky-named.jar`
   The jar lives in `SlinkyNative\payload\` next to `OpenSlinkyPlus.exe` and `SlinkyNative.dll`.
2. Start the game and inject. The log prints the runtime namespace and the script folder.
3. The script folder is created next to the Minecraft profile:
   `%APPDATA%\.minecraft\slinky-scripts\`
   Write `config.json` there to change the control port or to require a token:

   ```json
   {"port": 47900, "token": "optional-shared-secret"}
   ```

4. The control channel listens on `0.0.0.0:47900` so a phone on the same network can reach it. If
   Windows asks about the firewall, allow it on private networks, or add the rule by hand:

   ```
   netsh advfirewall firewall add rule name="OpenSlinky scripts" dir=in action=allow protocol=TCP localport=47900 profile=private
   ```

## Phone side (opencode)

The MCP server is installed at
`~/.local/share/opencode/mcp/slinky_mcp_server.py` and registered in
`~/.config/opencode/opencode.jsonc` as the `slinky` server.

Point it at the machine that runs Minecraft:

```json
{
  "host": "192.168.1.50",
  "port": 47900,
  "token": "",
  "workspace": "/data/data/com.termux/files/home/.slinky-mcp/scripts"
}
```

`~/.config/slinky-mcp.json` holds that file. Check the wiring with `opencode mcp list`.

## Writing a module

A module is a single `.js` file in the scripts folder. The first line may carry the manifest:

```javascript
// @slinky {"id":"aim_log","name":"Aim Log","category":"combat",
//          "settings":[{"key":"rate","label":"Rate","type":"number","default":20,"min":1,"max":100,"step":1}],
//          "listens":[14]}

var hits = 0;
function onEnable(api) { api.log('armed'); }
function onEvent(api, ev) {
   if (ev.name === 'pre_interact') {
      hits++;
      if (hits % api.num('rate') === 0) {
         var t = api.target();
         if (t) { api.lookAt(t.x, t.y, t.z); }
      }
   }
}
```

Callbacks: `onEnable`, `onDisable`, `onTick`, `onEvent`. The full api surface is returned by the
`slinky_api_docs` tool of the MCP server.

Script modules show up in the click gui like any other module, keep their settings in the
configuration file and survive a reload of the client.

## Rebuilding

On a machine with a JDK 17, gson 2.2.4, rhino 1.7.14 and the asm 9.7 jars in `LIBS`:

```
bash tools/build-payload.sh
```

The script compiles the changed sources, relocates rhino, injects everything into the three payload
jars, verifies them with `tools/verify_payload.py` and runs the offline smoke tests.
