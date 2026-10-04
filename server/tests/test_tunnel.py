import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from tunnel import (  # noqa: E402
    TunnelConfig,
    TunnelManager,
    TunnelState,
    load_tunnel_config,
    parse_tunnel_id,
    route_dns,
    save_tunnel_config,
    write_ingress_config,
)


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


def test_config_roundtrip_and_corrupt_defaults(tmp_path):
    cfg_dir = tmp_path / "cfg"
    save_tunnel_config(cfg_dir, TunnelConfig("id-1", "pc", "t.example.com", True))
    loaded = load_tunnel_config(cfg_dir)
    assert loaded == TunnelConfig("id-1", "pc", "t.example.com", True)

    (cfg_dir / "tunnel.json").write_text("not json {{{", encoding="utf-8")
    assert load_tunnel_config(cfg_dir) == TunnelConfig()

    assert load_tunnel_config(tmp_path / "missing-dir") == TunnelConfig()


def test_parse_tunnel_id_variants():
    assert parse_tunnel_id("Created tunnel t1 id=aaa") == "aaa"
    assert parse_tunnel_id("Created tunnel t1 with id bbb") == "bbb"
    assert (
        parse_tunnel_id(
            "some output\n11111111-2222-3333-4444-555555555555\n"
        )
        == "11111111-2222-3333-4444-555555555555"
    )
    assert parse_tunnel_id("some output\nsome error text") is None


def test_write_ingress_config_exact_content(tmp_path):
    cfg_dir = tmp_path / "cfg"
    path = write_ingress_config(cfg_dir, "tid", "pc.example.com", 8765)

    expected = (
        "tunnel: tid\n"
        "ingress:\n"
        "  - hostname: pc.example.com\n"
        "    service: http://localhost:8765\n"
        "  - service: http_status:404\n"
    )
    assert path == cfg_dir / "cloudflared" / "config.yml"
    assert path.read_text(encoding="utf-8") == expected


def test_route_dns_tolerates_already_exists(tmp_path):
    exists_dir = tmp_path / "already"
    exists_dir.mkdir()
    shim_exists = write_shim(
        exists_dir,
        "import sys\n"
        "print('ERR: pc.example.com already exists', file=sys.stderr)\n"
        "sys.exit(1)\n",
    )
    assert route_dns(shim_exists, "pc", "pc.example.com") is True

    denied_dir = tmp_path / "denied"
    denied_dir.mkdir()
    shim_denied = write_shim(
        denied_dir,
        "import sys\n"
        "print('permission denied', file=sys.stderr)\n"
        "sys.exit(1)\n",
    )
    assert route_dns(shim_denied, "pc", "pc.example.com") is False

    ok_dir = tmp_path / "ok"
    ok_dir.mkdir()
    shim_ok = write_shim(ok_dir, "print('ok')\n")
    assert route_dns(shim_ok, "pc", "pc.example.com") is True
