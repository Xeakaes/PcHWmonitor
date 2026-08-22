import asyncio
import json
import sys

import websockets


async def main() -> None:
    uri = sys.argv[1] if len(sys.argv) > 1 else "ws://127.0.0.1:8765/ws"
    token = sys.argv[2] if len(sys.argv) > 2 else None
    async with websockets.connect(uri) as ws:
        if token:
            await ws.send(json.dumps({"type": "auth", "token": token}))
        welcome = json.loads(await ws.recv())
        assert welcome["type"] == "welcome", f"expected welcome, got {welcome}"
        assert welcome["intervalMs"] > 0
        print(f"welcome: server={welcome['serverName']} source={welcome['source']}")

        got = []
        for _ in range(3):
            msg = json.loads(await ws.recv())
            assert msg["type"] == "status", f"expected status, got {msg['type']}"
            assert msg["available"] is True
            for section in ("cpu", "gpu", "ram", "pc"):
                assert section in msg, f"missing {section}"
            got.append(msg)
        print(f"status x{len(got)} ok: cpu.tempC={got[-1]['cpu']['tempC']} gpu.hotspotC={got[-1]['gpu']['hotspotC']} ram.usagePct={got[-1]['ram']['usagePct']}")
        print("SMOKE TEST PASSED")


asyncio.run(main())
