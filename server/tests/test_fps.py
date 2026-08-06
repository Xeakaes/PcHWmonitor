import sys
import threading
import time
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from adapters.fps import PresentMonFps, compute_fps, parse_csv_line  # noqa: E402


HEADER = "Application,ProcessID,SwapChainAddress,Runtime,msBetweenPresents,PresentMode,TimeInSeconds"


def test_parse_csv_line_extracts_application_and_frame_time():
    line = "game.exe,1234,0x1,DXGI,16.7,Hardware: Independent Flip,1.000"
    app, frame_ms = parse_csv_line(HEADER.split(","), line)
    assert app == "game.exe"
    assert abs(frame_ms - 16.7) < 1e-9


def test_parse_csv_line_rejects_garbage():
    assert parse_csv_line(HEADER.split(","), "not a csv line") is None
    assert parse_csv_line(HEADER.split(","), "a,b,c") is None
    assert parse_csv_line(HEADER.split(","), "game.exe,1,2,3,abc,4,5") is None


def test_parse_csv_line_missing_column_returns_none():
    header = "Application,ProcessID,PresentMode"
    assert parse_csv_line(header.split(","), "game.exe,1234,Flip") is None


def test_compute_fps_returns_none_for_few_entries():
    assert compute_fps([("game.exe", 16.7)], time.time()) is None


def test_compute_fps_math():
    now = time.time()
    # 60 fps for the whole window: 16.6667 ms, but one slow frame of 200 ms
    entries = [("game.exe", 16.7)] * 60 + [("game.exe", 200.0)] + [("game.exe", 16.7)] * 30
    info = compute_fps(entries, now)
    assert info is not None
    assert info.name == "game.exe"
    assert 55 <= info.current <= 62          # mean ~16.7-17 ms window
    assert 50 <= info.avg <= 62              # 1703/91 = 18.71 ms -> 53.5 fps
    assert info.onePercentLow < info.avg     # the 200 ms outlier drags p99 down
    assert info.onePercentLow >= 4.0         # 1000/200 = 5 fps floor


class _FakeStdout:
    def __init__(self, lines):
        self._lines = lines

    def __iter__(self):
        return iter(self._lines)


class _LiveStdout:
    """Simulates a live pipe: yields lines, never EOFs until closed."""

    def __init__(self, lines):
        self._lines = list(lines)
        self._done = False

    def __iter__(self):
        return self

    def __next__(self):
        while True:
            if self._lines:
                return self._lines.pop(0)
            if self._done:
                raise StopIteration
            time.sleep(0.005)


class _FakeProc:
    def __init__(self, lines):
        self.stdout = _FakeStdout(lines)
        self.stderr = None

    def terminate(self):
        self.stdout = None

    def wait(self, timeout=None):
        return 0

    def kill(self):
        self.stdout = None


def test_read_eof_clears_entries_and_sample_returns_none():
    lines = [
        HEADER + "\n",
        "game.exe,1234,0x1,DXGI,16.7,Hardware: Independent Flip,1.000\n",
        "game.exe,1234,0x1,DXGI,16.7,Hardware: Independent Flip,1.100\n",
    ]
    adapter = PresentMonFps("presentmon.exe")
    adapter._proc = _FakeProc(lines)
    adapter._read()
    assert adapter._stop.is_set()
    assert not adapter._entries
    assert adapter.sample() is None


def test_read_handles_bom_and_trailing_whitespace_in_header():
    lines = [
        "\ufeff" + HEADER + "\r\n",
        "game.exe,1234,0x1,DXGI,16.7,Hardware: Independent Flip,1.000\n",
    ]
    adapter = PresentMonFps("presentmon.exe")
    adapter._proc = _FakeProc(lines)
    adapter._read()
    assert adapter._stop.is_set()
    assert not adapter._entries
    assert adapter.sample() is None


def test_sample_returns_none_after_stop_without_read():
    adapter = PresentMonFps("presentmon.exe")
    adapter._proc = _FakeProc([HEADER + "\n", "game.exe,1234,0x1,DXGI,16.7,Hardware: Independent Flip,1.000\n"])
    adapter.stop()
    assert adapter._stop.is_set()
    assert adapter.sample() is None


def test_adapter_restorable_after_eof_and_failed_start():
    lines = [
        HEADER + "\n",
        "game.exe,1234,0x1,DXGI,16.7,Hardware: Independent Flip,1.000\n",
        "game.exe,1234,0x1,DXGI,16.7,Hardware: Independent Flip,1.100\n",
    ]
    adapter = PresentMonFps("presentmon.exe")

    adapter._proc = _FakeProc(lines)
    adapter._read()
    assert adapter.sample() is None

    with mock.patch("adapters.fps.subprocess.Popen", side_effect=OSError("no presentmon")):
        assert adapter.start() is False
    assert not adapter._stop.is_set()
    assert adapter.sample() is None

    live = _LiveStdout(lines)
    adapter._proc = _FakeProc(live)
    reader = threading.Thread(target=adapter._read, daemon=True)
    reader.start()
    deadline = time.monotonic() + 2.0
    while len(adapter._entries) < 2 and time.monotonic() < deadline:
        time.sleep(0.005)
    info = adapter.sample()
    assert info is not None
    assert info.name == "game.exe"
    assert info.current > 0
    live._done = True
    reader.join(timeout=2.0)
    assert adapter.sample() is None
