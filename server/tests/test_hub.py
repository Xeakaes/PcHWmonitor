import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from hub import Hub  # noqa: E402
from schema import StatusMessage  # noqa: E402


class FakeWs:
    def __init__(self, delay: float = 0.0):
        self.delay = delay
        self.sent = []

    async def send_text(self, payload: str) -> None:
        if self.delay:
            await asyncio.sleep(self.delay)
        self.sent.append(payload)


def _message() -> StatusMessage:
    return StatusMessage(timestamp=1234, available=True)


def test_broadcast_reaches_all_clients():
    hub = Hub(sample=_message, interval_ms=1000)
    a, b = FakeWs(), FakeWs()
    hub.register(a)
    hub.register(b)

    asyncio.run(hub._broadcast_current())

    assert a.sent == [_message().model_dump_json()]
    assert b.sent == [_message().model_dump_json()]


def test_slow_client_does_not_block_fast_clients():
    hub = Hub(sample=_message, interval_ms=1000, send_timeout=0.02)
    slow = FakeWs(delay=0.5)
    fast = FakeWs()
    hub.register(slow)
    hub.register(fast)

    asyncio.run(hub._broadcast_current())

    assert fast.sent == [_message().model_dump_json()]
    assert slow.sent == []
    # slow client stays registered; only hard failures unregister
    assert hub.client_count == 2


def test_failed_client_is_unregistered():
    hub = Hub(sample=_message, interval_ms=1000)
    bad = FakeWs()

    async def boom():
        raise ConnectionError("socket gone")

    bad.send_text = boom
    hub.register(bad)

    asyncio.run(hub._broadcast_current())

    assert hub.client_count == 0
