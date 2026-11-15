#!/usr/bin/env python3
"""
MCP server for OpenSlinky+ script modules.

Speaks the Model Context Protocol over stdio and talks to the running game over the
line delimited JSON control channel that the payload opens (default 0.0.0.0:47900).

Configuration (environment or ~/.config/slinky-mcp.json):
    SLINKY_HOST     lan address of the machine running Minecraft
    SLINKY_PORT     control port (default 47900)
    SLINKY_TOKEN    optional shared token
    SLINKY_WORKSPACE local folder where script sources are kept (default ~/.slinky-mcp/scripts)
"""
import json
import os
import socket
import sys
import time
from pathlib import Path

PROTOCOL_VERSION = "2024-11-05"
DEFAULT_PORT = 47900
SERVER_NAME = "slinky-scripts"

API_DOCS = """OpenSlinky+ script modules.

A module is a single JavaScript file in the scripts folder (Minecraft profile folder / slinky-scripts).
The first line may carry a manifest:

    // @slinky {"id":"my_module","name":"My Module","description":"what it does","category":"utility",
    //          "settings":[{"key":"range","label":"Range","type":"number","default":3.5,"min":1,"max":5,"step":0.5}],
    //          "listens":[0,"pre_interact"]}

manifest fields:
    id          unique module id (letters, digits, _ and -); defaults to the file name
    name        display name shown in the click gui
    description one line description
    category    combat | movement | render | blocks | utility   (default utility)
    settings    list of settings, types: boolean, number, choice, range, list
                number: {key,label,type,default,min,max,step}
                choice: {key,label,type,default,options:["a","b"]}
                range:  {key,label,type,default,min,max}
                boolean:{key,label,type,default}
    listens     event ids or names the module subscribes to (default: none)

callbacks (all optional):
    function onEnable(api)      called when the module is switched on
    function onDisable(api)     called when the module is switched off
    function onTick(api)        called every client tick while enabled
    function onEvent(api, ev)   called for every subscribed event

api surface:
    api.id, api.name, api.enabled, api.tick, api.now()
    api.log(message)                     writes to the script log and the game console
    api.setting(key)                     current value of a setting
    api.set(key, value)                  change a setting (number, boolean, label)
    api.num(key), api.bool(key), api.text(key)
    api.player()                         {x,y,z,yaw,pitch,health,maxHealth,food,onGround,sneaking,sprinting,name,id,inWorld}
    api.entities()                       list of {id,name,type,player,x,y,z,dx,dy,dz,distance,health,dead}
    api.target()                         nearest living entity or null
    api.blockAt(x,y,z)                   {id,name,air,x,y,z}
    api.heldItem()                       {id,name,empty}
    api.hotbar(slot)                     {id,name,slot,empty}
    api.swing(), api.attack(name), api.say(message)
    api.relationshipFlags(name)          relationship bitmask of an entity
    api.setFriend(name, true|false)      mark an entity as friend
    api.look(yaw, pitch)                 hold an absolute rotation
    api.lookDelta(dyaw, dpitch)          add a smooth rotation delta
    api.lookAt(x, y, z)                  aim at a world position
    api.clearRotation()                  drop the manual rotation
    api.cameraYaw(), api.cameraPitch(), api.currentYaw(), api.currentPitch()
    api.claimRotation(priority)          true when the rotation slot is yours
    api.noise(value)                     aim smoothing noise helper
    api.modules()                        names of all modules
    api.setModuleEnabled(id, on)         switch another module

event object (onEvent):
    ev.type, ev.name, ev.cancelled, ev.cancel(), ev.active, ev.enabled, ev.jump, ev.sneaking,
    ev.overrideRotation, ev.remapMovement, ev.reducing, ev.yaw, ev.pitch, ev.distance, ev.expand,
    ev.forward, ev.sideways, ev.factor, ev.scale, ev.elapsedSeconds, ev.dispatchMode

event ids:
    0 tick, 1 render_tick, 2 packet_inbound, 3 packet_outbound, 4 cancel_disable, 5 lifecycle,
    6 render_world, 7 render_ortho, 8 ray_trace, 9 gui_canvas, 10 gui_draw, 11 gui_action,
    12 gui_error, 13 rotation, 14 pre_interact, 15 post_interact, 16 pre_entities,
    17 begin_movement, 18 post_entities, 19 friends, 20 block_event, 21 pre_move, 22 post_move,
    23 packet_send, 24 binding, 25 controller, 26 reset

example:

    // @slinky {"id":"aim_log","name":"Aim Log","category":"combat","settings":[{"key":"rate","label":"Rate","type":"number","default":20,"min":1,"max":100,"step":1}]}
    var hits = 0;
    function onEnable(api) { api.log('armed'); }
    function onEvent(api, ev) {
      if (ev.name === 'pre_interact') {
        hits++;
        if (hits % api.num('rate') === 0) {
          var t = api.target();
          if (t) { api.look(t.dx, t.dy); api.log('aiming at ' + t.name); }
        }
      }
    }
"""


def load_config():
    config = {}
    path = Path(os.environ.get("SLINKY_CONFIG", Path.home() / ".config" / "slinky-mcp.json"))
    if path.is_file():
        try:
            config.update(json.loads(path.read_text()))
        except Exception:
            pass
    for key in ("host", "port", "token", "workspace"):
        env = os.environ.get("SLINKY_" + key.upper())
        if env:
            config[key] = env
    config.setdefault("port", DEFAULT_PORT)
    config.setdefault("host", "127.0.0.1")
    config.setdefault("token", "")
    config.setdefault("workspace", str(Path.home() / ".slinky-mcp" / "scripts"))
    return config


class Game:
    def __init__(self, config):
        self.host = config["host"]
        self.port = int(config["port"])
        self.token = config["token"]

    def call(self, command, timeout=10.0, **payload):
        request = {"cmd": command}
        request.update(payload)
        if self.token:
            request["token"] = self.token
        with socket.create_connection((self.host, self.port), timeout=timeout) as sock:
            sock.settimeout(timeout)
            stream = sock.makefile("rwb")
            stream.write(json.dumps(request).encode("utf-8") + b"\n")
            stream.flush()
            line = stream.readline()
            if not line:
                raise RuntimeError("no response from the game")
            answer = json.loads(line.decode("utf-8"))
        if not answer.get("ok"):
            raise RuntimeError(answer.get("error", "unknown error"))
        return answer.get("result", {})

    def online(self):
        try:
            self.call("ping", timeout=3.0)
            return True
        except Exception:
            return False


class Workspace:
    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)

    def path(self, name):
        if not name or "/" in name or "\\" in name or name.startswith("."):
            raise ValueError("invalid script id")
        return self.root / (name + ".js")

    def list(self):
        return sorted(p.stem for p in self.root.glob("*.js"))

    def read(self, name):
        return self.path(name).read_text(encoding="utf-8")

    def write(self, name, source):
        target = self.path(name)
        target.write_text(source, encoding="utf-8")
        return target


def validate(source):
    problems = []
    if "// @slinky" in source:
        start = source.index("// @slinky") + len("// @slinky")
        depth = 0
        end = -1
        for index in range(start, len(source)):
            char = source[index]
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    end = index + 1
                    break
        if end < 0:
            problems.append("manifest json is not closed")
        else:
            try:
                manifest = json.loads(source[start:end])
            except Exception as error:
                problems.append("manifest json invalid: %s" % error)
                manifest = None
            if isinstance(manifest, dict):
                if not manifest.get("id"):
                    problems.append("manifest id is missing")
                for setting in manifest.get("settings", []) or []:
                    if not setting.get("key"):
                        problems.append("a setting has no key")
                    if setting.get("type") not in (None, "boolean", "number", "choice", "range", "list"):
                        problems.append("unknown setting type %s" % setting.get("type"))
    for opener, closer in (("{", "}"), ("(", ")"), ("[", "]")):
        if source.count(opener) != source.count(closer):
            problems.append("unbalanced %s%s in the script" % (opener, closer))
    for name in ("onTick", "onEvent", "onEnable", "onDisable"):
        if "function %s(" % name in source and name not in ("onTick",):
            pass
    return problems


TOOLS = [
    {
        "name": "slinky_status",
        "description": "Check the game connection and report namespace, scripts folder and loaded modules.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "slinky_list_scripts",
        "description": "List every script module known to the game with its state, error and tick counter.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "slinky_read_script",
        "description": "Read the source of a script module from the game.",
        "inputSchema": {
            "type": "object",
            "properties": {"id": {"type": "string", "description": "script id"}},
            "required": ["id"],
        },
    },
    {
        "name": "slinky_write_script",
        "description": "Write a script module. Writes to the local workspace and, when deploy is true, uploads it to the running game where it is hot reloaded.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string", "description": "script id, letters digits _ and -"},
                "source": {"type": "string", "description": "full javascript source including the manifest line"},
                "deploy": {"type": "boolean", "description": "upload to the running game (default true)"},
            },
            "required": ["id", "source"],
        },
    },
    {
        "name": "slinky_validate_script",
        "description": "Check a script for manifest and brace problems before deploying it.",
        "inputSchema": {
            "type": "object",
            "properties": {"source": {"type": "string"}},
            "required": ["source"],
        },
    },
    {
        "name": "slinky_delete_script",
        "description": "Delete a script module from the game.",
        "inputSchema": {
            "type": "object",
            "properties": {"id": {"type": "string"}},
            "required": ["id"],
        },
    },
    {
        "name": "slinky_set_module_enabled",
        "description": "Switch a module on or off inside the game.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string"},
                "on": {"type": "boolean"},
            },
            "required": ["id", "on"],
        },
    },
    {
        "name": "slinky_set_setting",
        "description": "Change a setting of a module in the game.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "string"},
                "key": {"type": "string"},
                "value": {"type": "string"},
            },
            "required": ["id", "key", "value"],
        },
    },
    {
        "name": "slinky_logs",
        "description": "Read the log lines a script module produced.",
        "inputSchema": {
            "type": "object",
            "properties": {"id": {"type": "string"}},
            "required": ["id"],
        },
    },
    {
        "name": "slinky_api_docs",
        "description": "Full documentation of the script api, the manifest format and the event list.",
        "inputSchema": {"type": "object", "properties": {}},
    },
]


def tool_result(text, error=False):
    return {"content": [{"type": "text", "text": text}], "isError": bool(error)}


def handle_tool(name, arguments, game, workspace):
    if name == "slinky_status":
        if not game.online():
            return tool_result("The game is not reachable at %s:%s." % (game.host, game.port), True)
        result = game.call("status")
        return tool_result(json.dumps(result, indent=2))

    if name == "slinky_list_scripts":
        if not game.online():
            return tool_result("The game is not reachable at %s:%s." % (game.host, game.port), True)
        result = game.call("list")
        rows = result.get("scripts", [])
        if not rows:
            return tool_result("no script modules are loaded")
        lines = []
        for row in rows:
            lines.append(
                "%s (%s) state=%s enabled=%s ticks=%s%s"
                % (row.get("name"), row.get("id"), row.get("state"), row.get("enabled"), row.get("tick"),
                   (" error=" + str(row.get("error"))) if row.get("error") else "")
            )
        return tool_result("\n".join(lines))

    if name == "slinky_read_script":
        script_id = arguments.get("id", "")
        try:
            source = game.call("get", id=script_id).get("source", "")
        except Exception as error:
            return tool_result(str(error), True)
        try:
            workspace.write(script_id, source)
        except Exception:
            pass
        return tool_result(source)

    if name == "slinky_write_script":
        script_id = arguments.get("id", "")
        source = arguments.get("source", "")
        deploy = arguments.get("deploy", True)
        problems = validate(source)
        try:
            workspace.write(script_id, source)
        except Exception as error:
            return tool_result("invalid script id: %s" % error, True)
        if problems:
            return tool_result("written to the workspace but not deployed:\n- " + "\n- ".join(problems), True)
        if not deploy:
            return tool_result("written to %s" % workspace.path(script_id))
        if not game.online():
            return tool_result("written to %s but the game is not reachable at %s:%s" % (workspace.path(script_id), game.host, game.port), True)
        try:
            game.call("put", id=script_id, source=source)
        except Exception as error:
            return tool_result("deploy failed: %s" % error, True)
        time.sleep(0.4)
        state = ""
        try:
            for row in game.call("list").get("scripts", []):
                if row.get("file") == script_id or row.get("id") == script_id:
                    state = " (%s%s)" % (row.get("state"), (" error=" + str(row.get("error"))) if row.get("error") else "")
        except Exception:
            pass
        return tool_result("deployed %s%s" % (script_id, state))

    if name == "slinky_validate_script":
        problems = validate(arguments.get("source", ""))
        if problems:
            return tool_result("problems:\n- " + "\n- ".join(problems), True)
        return tool_result("no problems found")

    if name == "slinky_delete_script":
        script_id = arguments.get("id", "")
        try:
            game.call("delete", id=script_id)
        except Exception as error:
            return tool_result(str(error), True)
        return tool_result("deleted %s" % script_id)

    if name == "slinky_set_module_enabled":
        try:
            game.call("enable", id=arguments.get("id", ""), on=bool(arguments.get("on")))
        except Exception as error:
            return tool_result(str(error), True)
        return tool_result("%s is now %s" % (arguments.get("id"), "on" if arguments.get("on") else "off"))

    if name == "slinky_set_setting":
        try:
            game.call("setting", id=arguments.get("id", ""), key=arguments.get("key", ""), value=arguments.get("value", ""))
        except Exception as error:
            return tool_result(str(error), True)
        return tool_result("set %s.%s = %s" % (arguments.get("id"), arguments.get("key"), arguments.get("value")))

    if name == "slinky_logs":
        script_id = arguments.get("id", "")
        try:
            lines = game.call("logs", id=script_id).get("lines", [])
        except Exception as error:
            return tool_result(str(error), True)
        if not lines:
            return tool_result("no log lines")
        return tool_result("\n".join(lines[-100:]))

    if name == "slinky_api_docs":
        return tool_result(API_DOCS)

    return tool_result("unknown tool " + name, True)


def send(message):
    sys.stdout.write(json.dumps(message) + "\n")
    sys.stdout.flush()


def main():
    config = load_config()
    game = Game(config)
    workspace = Workspace(config["workspace"])

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            message = json.loads(line)
        except Exception:
            continue
        method = message.get("method")
        request_id = message.get("id")

        if method == "initialize":
            send({
                "jsonrpc": "2.0",
                "id": request_id,
                "result": {
                    "protocolVersion": PROTOCOL_VERSION,
                    "capabilities": {"tools": {"listChanged": False}},
                    "serverInfo": {"name": SERVER_NAME, "version": "1.0.0"},
                },
            })
        elif method == "notifications/initialized":
            continue
        elif method == "tools/list":
            send({"jsonrpc": "2.0", "id": request_id, "result": {"tools": TOOLS}})
        elif method == "tools/call":
            params = message.get("params", {})
            name = params.get("name", "")
            arguments = params.get("arguments", {}) or {}
            try:
                result = handle_tool(name, arguments, game, workspace)
            except Exception as error:
                result = tool_result("tool failed: %s" % error, True)
            send({"jsonrpc": "2.0", "id": request_id, "result": result})
        elif method == "ping":
            send({"jsonrpc": "2.0", "id": request_id, "result": {}})
        elif request_id is not None:
            send({"jsonrpc": "2.0", "id": request_id, "error": {"code": -32601, "message": "method not found"}})


if __name__ == "__main__":
    main()
