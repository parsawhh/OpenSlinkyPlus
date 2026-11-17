#!/usr/bin/env python3
"""
Offline test for the MCP server: drives it with raw JSON-RPC messages against a standalone
control channel (no Minecraft needed).

   python3 tools/mcp/test_mcp_server.py
"""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MCP = ROOT / "tools" / "mcp" / "slinky_mcp_server.py"
WORKSPACE = Path("/tmp/slinky-mcp-test-workspace")
PORT = 47961

failures = 0


def check(label, expected, actual):
    global failures
    ok = expected == actual
    if not ok:
        failures += 1
    print(("  ok   " if ok else "  FAIL ") + label + " expected=" + repr(expected) + " actual=" + repr(actual))


def rpc(process, method, params=None, request_id=1):
    message = {"jsonrpc": "2.0", "id": request_id, "method": method}
    if params is not None:
        message["params"] = params
    process.stdin.write(json.dumps(message) + "\n")
    process.stdin.flush()
    line = process.stdout.readline()
    if not line:
        raise RuntimeError("no response for " + method)
    return json.loads(line)


def main():
    workspace = WORKSPACE
    if workspace.exists():
        for path in workspace.glob("*"):
            path.unlink()
    workspace.mkdir(parents=True, exist_ok=True)

    server = subprocess.Popen(
        ["java", "-cp", os.environ.get("SLINKY_TEST_CLASSPATH", "."), "dev.slinky.script.ScriptServerStandalone", str(workspace), str(PORT)],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    time.sleep(2.5)

    env = dict(os.environ)
    env["SLINKY_HOST"] = "127.0.0.1"
    env["SLINKY_PORT"] = str(PORT)
    env["SLINKY_WORKSPACE"] = str(workspace / "local")
    process = subprocess.Popen([sys.executable, str(MCP)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env, text=True)

    try:
        init = rpc(process, "initialize", {"protocolVersion": "2024-11-05", "capabilities": {}})
        check("initialize protocol", "2024-11-05", init["result"]["protocolVersion"])
        check("initialize name", "slinky-scripts", init["result"]["serverInfo"]["name"])

        tools = rpc(process, "tools/list")
        names = sorted(tool["name"] for tool in tools["result"]["tools"])
        check("tool count", 10, len(names))
        check("has write tool", True, "slinky_write_script" in names)
        check("has api docs", True, "slinky_api_docs" in names)

        docs = rpc(process, "tools/call", {"name": "slinky_api_docs", "arguments": {}})
        check("api docs size", True, len(docs["result"]["content"][0]["text"]) > 2000)

        script = (
            '// @slinky {"id":"demo","name":"Demo","category":"utility","settings":'
            '[{"key":"rate","label":"Rate","type":"number","default":5,"min":1,"max":20,"step":1}],"listens":[0]}\n'
            "function onTick(api){ api.log('tick ' + api.tick); }\n"
        )

        invalid = rpc(process, "tools/call", {"name": "slinky_validate_script", "arguments": {"source": "// @slinky {\"id\": \"x\"}\nfunction onTick(){"}})
        check("invalid script flagged", True, invalid["result"]["isError"])

        valid = rpc(process, "tools/call", {"name": "slinky_validate_script", "arguments": {"source": script}})
        check("valid script accepted", False, valid["result"]["isError"])

        write = rpc(process, "tools/call", {"name": "slinky_write_script", "arguments": {"id": "demo", "source": script}})
        check("write deployed", False, write["result"]["isError"])
        check("file on disk", True, (workspace / "demo.js").is_file())

        read = rpc(process, "tools/call", {"name": "slinky_read_script", "arguments": {"id": "demo"}})
        check("read matches", script, read["result"]["content"][0]["text"])

        status = rpc(process, "tools/call", {"name": "slinky_status", "arguments": {}})
        check("status ok", False, status["result"]["isError"])

        listing = rpc(process, "tools/call", {"name": "slinky_list_scripts", "arguments": {}})
        check("list ok", False, listing["result"]["isError"])

        enable = rpc(process, "tools/call", {"name": "slinky_set_module_enabled", "arguments": {"id": "demo", "on": True}})
        check("enable ok", False, enable["result"]["isError"])

        delete = rpc(process, "tools/call", {"name": "slinky_delete_script", "arguments": {"id": "demo"}})
        check("delete ok", False, delete["result"]["isError"])
        check("file removed", False, (workspace / "demo.js").is_file())
    finally:
        process.stdin.close()
        process.terminate()
        server.terminate()

    if failures == 0:
        print("mcp server test: OK")
        return 0
    print("mcp server test: %d FAILURES" % failures)
    return 1


if __name__ == "__main__":
    sys.exit(main())
