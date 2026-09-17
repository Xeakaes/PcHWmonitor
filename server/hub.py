import asyncio
import logging
import time

from fastapi import WebSocket

logger = logging.getLogger("pchw.hub")


class Hub:
    def __init__(self, sample, interval_ms: int = 1000, send_timeout: float = 2.0):
        self._sample = sample
        self._interval = interval_ms / 1000.0
        self._send_timeout = send_timeout
        self._clients: set[WebSocket] = set()
        self._sending: set[WebSocket] = set()
        self._last_error_at = 0.0

    def register(self, ws: WebSocket) -> None:
        self._clients.add(ws)

    def unregister(self, ws: WebSocket) -> None:
        self._clients.discard(ws)
        self._sending.discard(ws)

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
        message = await asyncio.to_thread(self._sample)
        if not message.available and time.monotonic() - self._last_error_at < 5.0:
            return
        if not message.available:
            self._last_error_at = time.monotonic()
            logger.warning("data source unavailable: %s", message.error)
        payload = message.model_dump_json()
        # Snapshot clients; skip any currently mid-send to avoid concurrent writes
        clients = [ws for ws in list(self._clients) if ws not in self._sending]
        if not clients:
            return
        results = await asyncio.gather(
            *(self._send_one(ws, payload) for ws in clients),
            return_exceptions=True,
        )
        for ws, result in zip(clients, results):
            if isinstance(result, BaseException):
                logger.debug("client send crashed: %r", result)
                self.unregister(ws)
            elif result is False:
                self.unregister(ws)

    async def _send_one(self, ws: WebSocket, payload: str) -> bool:
        """Deliver one frame; False marks a dead connection."""
        self._sending.add(ws)
        try:
            await asyncio.wait_for(ws.send_text(payload), timeout=self._send_timeout)
            return True
        except asyncio.TimeoutError:
            logger.warning("slow client timed out, disconnecting")
            try:
                await ws.close(code=1008, reason="slow client")
            except Exception:
                pass
            return False
        except Exception as exc:
            logger.debug("send failed, dropping client: %s", exc)
            return False
        finally:
            self._sending.discard(ws)
