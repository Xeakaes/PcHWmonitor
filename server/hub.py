import asyncio
import json
import logging
import time

from fastapi import WebSocket
from pydantic.json import pydantic_encoder

logger = logging.getLogger("pchw.hub")


class Hub:
    def __init__(self, sample, interval_ms: int = 1000):
        self._sample = sample
        self._interval = interval_ms / 1000.0
        self._clients: set[WebSocket] = set()
        self._last_error_at = 0.0

    def register(self, ws: WebSocket) -> None:
        self._clients.add(ws)

    def unregister(self, ws: WebSocket) -> None:
        self._clients.discard(ws)

    @property
    def client_count(self) -> int:
        return len(self._clients)

    async def tick_forever(self) -> None:
        while True:
            started = time.monotonic()
            await self._broadcast_current()
            elapsed = time.monotonic() - started
            await asyncio.sleep(max(0.05, self._interval - elapsed))

    async def _broadcast_current(self) -> None:
        message = self._sample()
        if not message.available and time.monotonic() - self._last_error_at < 5.0:
            return
        if not message.available:
            self._last_error_at = time.monotonic()
            logger.warning("data source unavailable: %s", message.error)
        payload = json.dumps(message, default=pydantic_encoder)
        stale = []
        for ws in list(self._clients):
            try:
                await ws.send_text(payload)
            except Exception:
                stale.append(ws)
        for ws in stale:
            self.unregister(ws)
