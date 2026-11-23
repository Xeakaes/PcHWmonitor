import csv
import io
import math
import subprocess
import threading
import time
from collections import deque

from schema import FpsInfo


def parse_csv_line(header: list[str], line: str) -> tuple[str, float] | None:
    try:
        idx_app = header.index("Application")
        idx_ms = header.index("msBetweenPresents")
    except ValueError:
        return None
    parts = next(csv.reader(io.StringIO(line)))
    if len(parts) <= max(idx_app, idx_ms):
        return None
    try:
        ms = float(parts[idx_ms])
    except ValueError:
        return None
    if not math.isfinite(ms) or ms <= 0:
        return None
    return parts[idx_app], ms


def _percentile(values: list[float], pct: float) -> float:
    ordered = sorted(values)
    idx = min(len(ordered) - 1, int(math.ceil(pct / 100.0 * len(ordered))) - 1)
    return ordered[idx]


def compute_fps(entries: list[tuple[str, float, float]], now: float, interval_s: float = 2.0, window_s: float = 30.0) -> FpsInfo | None:
    window = [e for e in entries if e[1] >= now - window_s]
    if not window:
        return None
    app = window[-1][0]
    frames = [(ts, ms) for name, ts, ms in window if name == app]
    if len(frames) < 2:
        return None
    recent = [f for f in frames if f[0] >= now - interval_s] or frames[-2:]
    current = 1000.0 / (sum(ms for _, ms in recent) / len(recent))
    avg = 1000.0 / (sum(ms for _, ms in frames) / len(frames))
    p99 = _percentile([ms for _, ms in frames], 99.0)
    return FpsInfo(name=app, current=round(current, 1), avg=round(avg, 1), onePercentLow=round(1000.0 / p99, 1))


class PresentMonFps:
    def __init__(self, exe_path: str, process_name: str | None = None, interval_s: float = 2.0, window_s: float = 30.0):
        self._exe_path = exe_path
        self._process_name = process_name
        self._interval_s = interval_s
        self._window_s = window_s
        self._entries: deque[tuple[str, float, float]] = deque(maxlen=60000)
        self._proc: subprocess.Popen | None = None
        self._thread: threading.Thread | None = None
        self._stop = threading.Event()

    def start(self) -> bool:
        self._stop.clear()
        self._entries.clear()
        cmd = [self._exe_path, "--output_stdout"]
        if self._process_name:
            cmd += ["--process_name", self._process_name]
        try:
            self._proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                text=True,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
        except OSError:
            self._proc = None
            return False
        self._thread = threading.Thread(target=self._read, daemon=True)
        self._thread.start()
        return True

    def _read(self) -> None:
        if self._proc is None or self._proc.stdout is None:
            return
        header: list[str] | None = None
        for raw in self._proc.stdout:
            line = raw.lstrip("\ufeff").strip()
            if header is None:
                if "msBetweenPresents" in line:
                    header = line.split(",")
                continue
            parsed = parse_csv_line(header, line)
            if parsed is not None:
                self._entries.append((parsed[0], time.monotonic(), parsed[1]))
        self._entries.clear()
        self._stop.set()

    def sample(self) -> FpsInfo | None:
        if not self._entries or self._stop.is_set():
            return None
        return compute_fps(list(self._entries), time.monotonic(), self._interval_s, self._window_s)

    def stop(self) -> None:
        proc, self._proc = self._proc, None
        if proc is not None:
            proc.terminate()
            try:
                proc.wait(timeout=3)
            except Exception:
                proc.kill()
            # Close the pipe so the reader thread's stdout loop sees EOF
            # instead of blocking forever on a dead child.
            try:
                if proc.stdout is not None:
                    proc.stdout.close()
            except Exception:
                pass
        if self._thread is not None:
            self._thread.join(timeout=3)
            self._thread = None
        self._stop.set()
