import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from tunnel import TunnelManager, TunnelState  # noqa: E402


def write_shim(tmp_path, body: str) -> Path:
    """Executable python script standing in for cloudflared; body = script source after shebang."""
    p = tmp_path / "cloudflared_shim"
    p.write_text("#!/usr/bin/env python3\n" + body, encoding="utf-8")
    p.chmod(0o755)
    return p


def test_start_quick_reaches_running_and_reports_url(tmp_path):
    shim = write_shim(
        tmp_path,
        "print('https://quick-test.trycloudflare.com', flush=True)\n"
        "import time\n"
        "time.sleep(60)\n",
    )
    url_event = threading.Event()
    state_event = threading.Event()
    urls = []

    def on_url(url: str) -> None:
        urls.append(url)
        url_event.set()

    def on_state(state: TunnelState) -> None:
        if state is TunnelState.RUNNING:
            state_event.set()

    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=tmp_path / "cfg",
        on_state=on_state,
        on_url=on_url,
    )

    assert mgr.start_quick(8765) is True
    assert state_event.wait(timeout=5)
    assert url_event.wait(timeout=5)
    assert urls == ["https://quick-test.trycloudflare.com"]
    assert mgr.state is TunnelState.RUNNING
    assert mgr.is_running is True
    assert mgr.url == "https://quick-test.trycloudflare.com"

    mgr.stop()
    assert mgr.state is TunnelState.OFF
    assert mgr.url is None
    assert mgr.is_running is False


def test_start_quick_missing_binary_sets_error(tmp_path):
    mgr = TunnelManager(cloudflared_finder=lambda: None, config_dir=tmp_path / "cfg")

    assert mgr.start_quick(8765) is False
    assert mgr.state is TunnelState.ERROR
    assert mgr.last_error is not None
    assert "cloudflared" in mgr.last_error


def test_start_refused_while_process_alive(tmp_path):
    shim = write_shim(
        tmp_path,
        "print('https://quick-test.trycloudflare.com', flush=True)\n"
        "import time\n"
        "time.sleep(60)\n",
    )
    url_event = threading.Event()
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=tmp_path / "cfg",
        on_url=lambda url: url_event.set(),
    )

    assert mgr.start_quick(8765) is True
    assert url_event.wait(timeout=5)

    assert mgr.start_quick(9999) is False
    assert mgr.tunnel_name == "quick"
    assert mgr.is_running is True

    mgr.stop()


def test_process_early_exit_marks_error(tmp_path):
    shim = write_shim(tmp_path, "import sys\nsys.exit(3)\n")
    error_event = threading.Event()

    def on_state(state: TunnelState) -> None:
        if state is TunnelState.ERROR:
            error_event.set()

    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=tmp_path / "cfg",
        on_state=on_state,
    )

    assert mgr.start_quick(8765) is True
    assert error_event.wait(timeout=5)
    assert mgr.state is TunnelState.ERROR
    assert mgr.last_error is not None
    assert "exited (3)" in mgr.last_error


def test_stop_kills_hung_process(tmp_path):
    shim = write_shim(
        tmp_path,
        "import signal\n"
        "signal.signal(signal.SIGTERM, signal.SIG_IGN)\n"
        "import time\n"
        "time.sleep(60)\n",
    )
    mgr = TunnelManager(cloudflared_finder=lambda: shim, config_dir=tmp_path / "cfg")

    assert mgr.start_quick(8765) is True
    assert mgr.is_running is True
    process = mgr._process
    assert process is not None

    started = time.monotonic()
    mgr.stop()
    elapsed = time.monotonic() - started

    assert elapsed < 6
    assert mgr.is_running is False
    assert mgr.state is TunnelState.OFF
    assert process.poll() is not None


def test_second_https_line_does_not_replace_url(tmp_path):
    shim = write_shim(
        tmp_path,
        "import time\n"
        "print('https://quick-test.trycloudflare.com', flush=True)\n"
        "time.sleep(0.2)\n"
        "print('see https://developers.cloudflare.com/docs for details', flush=True)\n"
        "time.sleep(60)\n",
    )
    url_event = threading.Event()
    urls = []

    def on_url(url: str) -> None:
        urls.append(url)
        url_event.set()

    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=tmp_path / "cfg",
        on_url=on_url,
    )

    assert mgr.start_quick(8765) is True
    assert url_event.wait(timeout=5)
    assert urls == ["https://quick-test.trycloudflare.com"]

    # Let the reader consume the later docs-URL line before asserting the latch.
    time.sleep(0.8)
    assert urls == ["https://quick-test.trycloudflare.com"]
    assert mgr.url == "https://quick-test.trycloudflare.com"

    mgr.stop()
