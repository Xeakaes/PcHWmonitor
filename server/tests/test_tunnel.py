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


def logged_shim(tmp_path, body: str) -> Path:
    """Shim that appends its argv (minus program name) to calls.log, then runs body."""
    calls = tmp_path / "calls.log"
    prefix = (
        "import sys, time\n"
        f"with open({str(calls)!r}, 'a') as fh:\n"
        "    fh.write(' '.join(sys.argv[1:]) + '\\n')\n"
    )
    return write_shim(tmp_path, prefix + body)


def wait_for_calls(tmp_path, needle: str, timeout: float = 5.0) -> str:
    """Poll calls.log until needle appears — shims log asynchronously after spawn."""
    path = tmp_path / "calls.log"
    deadline = time.monotonic() + timeout
    while True:
        text = path.read_text(encoding="utf-8") if path.exists() else ""
        if needle in text or time.monotonic() >= deadline:
            return text
        time.sleep(0.05)


EXISTING_TUNNEL_LIST = (
    'if sys.argv[1:3] == ["tunnel", "list"]:\n'
    '    print("ID                                   NAME     CREATED")\n'
    '    print("------------------------------------ -------- ----------")\n'
    '    print("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee pc       2026-01-01")\n'
    'elif sys.argv[1:3] == ["tunnel", "route"]:\n'
    '    print("Updated pc.example.com route")\n'
    "else:\n"
    "    time.sleep(60)\n"
)


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


def test_start_named_existing_tunnel_no_create_and_ingress_written(tmp_path):
    shim = logged_shim(tmp_path, EXISTING_TUNNEL_LIST)
    cert = tmp_path / "cert.pem"
    cert.write_text("cert", encoding="utf-8")
    cfg_dir = tmp_path / "cfg"
    urls = []
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=cfg_dir,
        cert_path=cert,
        on_url=urls.append,
    )

    assert mgr.start_named("pc", "pc.example.com", 8765) is True
    assert mgr.state is TunnelState.RUNNING
    assert mgr.url == "https://pc.example.com"
    assert urls == ["https://pc.example.com"]

    cfg = load_tunnel_config(cfg_dir)
    assert cfg.tunnel_name == "pc"
    assert cfg.tunnel_id == "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    assert cfg.hostname == "pc.example.com"
    assert cfg.auto_start is False

    config_text = (cfg_dir / "cloudflared" / "config.yml").read_text(encoding="utf-8")
    assert config_text == (
        "tunnel: aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\n"
        "ingress:\n"
        "  - hostname: pc.example.com\n"
        "    service: http://localhost:8765\n"
        "  - service: http_status:404\n"
    )

    calls = wait_for_calls(tmp_path, "tunnel run --config")
    assert "tunnel run --config" in calls
    assert "tunnel create" not in calls

    mgr.stop()


def test_ingress_rewritten_with_current_port(tmp_path):
    shim = logged_shim(tmp_path, EXISTING_TUNNEL_LIST)
    cert = tmp_path / "cert.pem"
    cert.write_text("cert", encoding="utf-8")
    cfg_dir = tmp_path / "cfg"
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=cfg_dir,
        cert_path=cert,
    )

    assert mgr.start_named("pc", "pc.example.com", 8765) is True
    first = (cfg_dir / "cloudflared" / "config.yml").read_text(encoding="utf-8")
    assert "localhost:8765" in first
    mgr.stop()

    assert mgr.start_named("pc", "pc.example.com", 9999) is True
    second = (cfg_dir / "cloudflared" / "config.yml").read_text(encoding="utf-8")
    assert "localhost:9999" in second
    assert "8765" not in second

    mgr.stop()


def test_start_named_missing_cert_errors(tmp_path):
    shim = logged_shim(tmp_path, EXISTING_TUNNEL_LIST)
    cfg_dir = tmp_path / "cfg"
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=cfg_dir,
        cert_path=tmp_path / "nonexistent-cert.pem",
    )

    assert mgr.start_named("pc", "pc.example.com", 8765) is False
    assert mgr.state is TunnelState.ERROR
    assert mgr.last_error == "Cloudflare login required"
    assert not (cfg_dir / "tunnel.json").exists()


def test_bootstrap_timeout_sets_error(tmp_path):
    shim = logged_shim(tmp_path, "time.sleep(60)\n")
    cert = tmp_path / "cert.pem"
    cert.write_text("cert", encoding="utf-8")
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=tmp_path / "cfg",
        cert_path=cert,
        bootstrap_timeout=1.0,
    )

    started = time.monotonic()
    assert mgr.start_named("pc", "pc.example.com", 8765) is False
    elapsed = time.monotonic() - started

    assert mgr.state is TunnelState.ERROR
    assert mgr.last_error is not None
    assert elapsed < 5


def test_create_failure_surfaces_output_tail(tmp_path):
    shim = logged_shim(
        tmp_path,
        'if sys.argv[1:3] == ["tunnel", "list"]:\n'
        '    print("ID                                   NAME     CREATED")\n'
        '    print("------------------------------------ -------- ----------")\n'
        'elif sys.argv[1:3] == ["tunnel", "create"]:\n'
        '    print("Account is out of credit")\n'
        "    sys.exit(1)\n"
        "else:\n"
        "    time.sleep(60)\n",
    )
    cert = tmp_path / "cert.pem"
    cert.write_text("cert", encoding="utf-8")
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=tmp_path / "cfg",
        cert_path=cert,
    )

    assert mgr.start_named("pc", "pc.example.com", 8765) is False
    assert mgr.state is TunnelState.ERROR
    assert mgr.last_error is not None
    assert "out of credit" in mgr.last_error


def test_route_dns_failure_aborts_start(tmp_path):
    shim = logged_shim(
        tmp_path,
        'if sys.argv[1:3] == ["tunnel", "list"]:\n'
        '    print("ID                                   NAME     CREATED")\n'
        '    print("------------------------------------ -------- ----------")\n'
        '    print("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee pc       2026-01-01")\n'
        'elif sys.argv[1:3] == ["tunnel", "route"]:\n'
        '    print("permission denied")\n'
        "    sys.exit(1)\n"
        "else:\n"
        "    time.sleep(60)\n",
    )
    cert = tmp_path / "cert.pem"
    cert.write_text("cert", encoding="utf-8")
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=tmp_path / "cfg",
        cert_path=cert,
    )

    assert mgr.start_named("pc", "pc.example.com", 8765) is False
    assert mgr.state is TunnelState.ERROR
    calls = (tmp_path / "calls.log").read_text(encoding="utf-8")
    assert "tunnel run" not in calls


def test_apply_auto_start_refused_when_already_running(tmp_path):
    shim = logged_shim(
        tmp_path,
        "print('https://quick-test.trycloudflare.com', flush=True)\n"
        "time.sleep(60)\n",
    )
    cert = tmp_path / "cert.pem"
    cert.write_text("cert", encoding="utf-8")
    cfg_dir = tmp_path / "cfg"
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=cfg_dir,
        cert_path=cert,
    )

    assert mgr.start_quick(8765) is True
    assert mgr.is_running is True
    process = mgr._process

    save_tunnel_config(
        cfg_dir,
        TunnelConfig("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", "pc", "pc.example.com", True),
    )
    wait_for_calls(tmp_path, "tunnel --url")
    assert mgr.apply_auto_start(8765) is False
    assert mgr.is_running is True
    assert mgr.tunnel_name == "quick"
    assert mgr._process is process
    assert process.poll() is None
    calls = (tmp_path / "calls.log").read_text(encoding="utf-8")
    assert len([l for l in calls.splitlines() if l.strip()]) == 1

    mgr.stop()


def test_apply_auto_start_starts_saved_tunnel(tmp_path):
    shim = logged_shim(tmp_path, EXISTING_TUNNEL_LIST)
    cert = tmp_path / "cert.pem"
    cert.write_text("cert", encoding="utf-8")
    cfg_dir = tmp_path / "cfg"
    save_tunnel_config(
        cfg_dir,
        TunnelConfig("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", "pc", "pc.example.com", True),
    )
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=cfg_dir,
        cert_path=cert,
    )

    assert mgr.apply_auto_start(8765) is True
    assert mgr.state is TunnelState.RUNNING
    assert mgr.url == "https://pc.example.com"
    assert mgr.tunnel_name == "pc"
    assert mgr.get_config().auto_start is True
    config_text = (cfg_dir / "cloudflared" / "config.yml").read_text(encoding="utf-8")
    assert "localhost:8765" in config_text

    mgr.stop()


def test_apply_auto_start_off_or_missing_returns_false(tmp_path):
    shim = logged_shim(tmp_path, EXISTING_TUNNEL_LIST)
    cert = tmp_path / "cert.pem"
    cert.write_text("cert", encoding="utf-8")
    cfg_dir = tmp_path / "cfg"
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=cfg_dir,
        cert_path=cert,
    )

    assert mgr.apply_auto_start(8765) is False
    assert mgr.state is TunnelState.OFF

    save_tunnel_config(
        cfg_dir,
        TunnelConfig("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", "pc", "pc.example.com", False),
    )
    assert mgr.apply_auto_start(8765) is False
    assert mgr.state is TunnelState.OFF
    assert mgr.is_running is False
    assert not (tmp_path / "calls.log").exists()


def test_login_reports_success_and_failure(tmp_path, monkeypatch):
    import webbrowser

    opened = []
    monkeypatch.setattr(webbrowser, "open", lambda url: opened.append(url))

    ok_dir = tmp_path / "ok"
    ok_dir.mkdir()
    ok_cert = ok_dir / "cert.pem"
    ok_shim = write_shim(
        ok_dir,
        "import sys, time\n"
        "if sys.argv[1:3] == ['tunnel', 'login']:\n"
        "    print('See https://example.com/docs first', flush=True)\n"
        "    print('Please open https://dash.cloudflare.com/authorize?abc=1', flush=True)\n"
        "    time.sleep(0.2)\n"
        f"    with open({str(ok_cert)!r}, 'w') as fh:\n"
        "        fh.write('cert')\n",
    )
    ok_mgr = TunnelManager(
        cloudflared_finder=lambda: ok_shim,
        config_dir=tmp_path / "ok-cfg",
        cert_path=ok_cert,
    )
    assert ok_mgr.login() is True
    assert opened == ["https://dash.cloudflare.com/authorize?abc=1"]

    fail_dir = tmp_path / "fail"
    fail_dir.mkdir()
    fail_cert = fail_dir / "cert.pem"
    fail_shim = write_shim(
        fail_dir,
        "import sys\n"
        "print('login failed')\n"
        "sys.exit(1)\n",
    )
    fail_mgr = TunnelManager(
        cloudflared_finder=lambda: fail_shim,
        config_dir=tmp_path / "fail-cfg",
        cert_path=fail_cert,
    )
    assert fail_mgr.login() is False
    assert not fail_cert.exists()


def test_set_auto_start_persists_via_get_config(tmp_path):
    cfg_dir = tmp_path / "cfg"
    mgr = TunnelManager(cloudflared_finder=lambda: None, config_dir=cfg_dir)

    assert mgr.get_config() == TunnelConfig()

    save_tunnel_config(cfg_dir, TunnelConfig("tid-1", "pc", "pc.example.com", False))
    mgr.set_auto_start(True)
    assert mgr.get_config() == TunnelConfig("tid-1", "pc", "pc.example.com", True)

    mgr.set_auto_start(False)
    assert mgr.get_config() == TunnelConfig("tid-1", "pc", "pc.example.com", False)


def test_tray_headless_and_accepts_shared_manager():
    import inspect
    import tray  # must import without pystray/tkinter/display (lazy imports stay lazy)
    sig = inspect.signature(tray._run_with_tray)
    assert "tunnel_manager" in sig.parameters


def test_cert_path_defaults_lazily(tmp_path):
    mgr = TunnelManager(cloudflared_finder=lambda: None, config_dir=tmp_path / "cfg")
    assert mgr.cert_path == Path.home() / ".cloudflared" / "cert.pem"


SLOW_LIST_FAILING_CREATE = (
    'if sys.argv[1:3] == ["tunnel", "list"]:\n'
    "    time.sleep(6)\n"
    '    print("ID                                   NAME     CREATED")\n'
    '    print("------------------------------------ -------- ----------")\n'
    'elif sys.argv[1:3] == ["tunnel", "create"]:\n'
    '    print("Account is out of credit")\n'
    "    sys.exit(1)\n"
    "else:\n"
    "    time.sleep(60)\n"
)

SLOW_LIST_CREATE_OK = (
    'if sys.argv[1:3] == ["tunnel", "list"]:\n'
    "    time.sleep(6)\n"
    '    print("ID                                   NAME     CREATED")\n'
    '    print("------------------------------------ -------- ----------")\n'
    '    print("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee pc       2026-01-01")\n'
    'elif sys.argv[1:3] == ["tunnel", "create"]:\n'
    '    print("Created tunnel pc id=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")\n'
    'elif sys.argv[1:3] == ["tunnel", "route"]:\n'
    '    print("Updated pc.example.com route")\n'
    "else:\n"
    "    time.sleep(60)\n"
)


def _bootstrapping_mgr(tmp_path, body: str, bootstrap_timeout: float = 5.0):
    shim = logged_shim(tmp_path, body)
    cert = tmp_path / "cert.pem"
    cert.write_text("cert", encoding="utf-8")
    return TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=tmp_path / "cfg",
        cert_path=cert,
        bootstrap_timeout=bootstrap_timeout,
    )


def test_starts_refused_while_bootstrap_in_progress(tmp_path):
    mgr = _bootstrapping_mgr(tmp_path, SLOW_LIST_FAILING_CREATE, bootstrap_timeout=5.0)
    results = {}

    def run_start():
        results["named"] = mgr.start_named("pc", "pc.example.com", 8765)

    thread = threading.Thread(target=run_start, daemon=True)
    thread.start()

    deadline = time.monotonic() + 5
    while mgr.state is not TunnelState.STARTING and time.monotonic() < deadline:
        time.sleep(0.01)
    assert mgr.state is TunnelState.STARTING

    assert mgr.start_quick(9999) is False
    assert mgr.state is TunnelState.STARTING
    assert mgr.start_named("pc", "pc.example.com", 9999) is False
    assert mgr.state is TunnelState.STARTING
    assert mgr.is_running is False

    thread.join(timeout=15)
    assert not thread.is_alive()
    assert results["named"] is False
    assert mgr.state is TunnelState.ERROR

    calls = (tmp_path / "calls.log").read_text(encoding="utf-8")
    assert "tunnel --url" not in calls
    assert "tunnel run" not in calls


def test_stop_during_bootstrap_cancels_start(tmp_path):
    mgr = _bootstrapping_mgr(tmp_path, SLOW_LIST_CREATE_OK, bootstrap_timeout=5.0)
    results = {}

    def run_start():
        results["named"] = mgr.start_named("pc", "pc.example.com", 8765)

    thread = threading.Thread(target=run_start, daemon=True)
    thread.start()

    deadline = time.monotonic() + 5
    while mgr.state is not TunnelState.STARTING and time.monotonic() < deadline:
        time.sleep(0.01)
    assert mgr.state is TunnelState.STARTING
    # Stop only once the bootstrap subprocess is actually in flight, so the
    # cancellation path after a completed step (not the entry guard) is covered.
    wait_for_calls(tmp_path, "tunnel list", timeout=6)

    mgr.stop()
    assert mgr.state is TunnelState.OFF

    thread.join(timeout=15)
    assert not thread.is_alive()
    assert results["named"] is False
    assert mgr.state is TunnelState.OFF
    assert mgr.is_running is False

    calls = (tmp_path / "calls.log").read_text(encoding="utf-8")
    assert "tunnel list" in calls
    assert "tunnel create" not in calls
    assert "tunnel run" not in calls
    assert "tunnel --url" not in calls


def test_save_tunnel_config_atomic_replace_and_last_wins(tmp_path, monkeypatch):
    import json
    import os as os_mod

    cfg_dir = tmp_path / "cfg"
    replace_calls = []
    real_replace = os_mod.replace

    def spy(src, dst):
        replace_calls.append((Path(src), Path(dst)))
        return real_replace(src, dst)

    monkeypatch.setattr(os_mod, "replace", spy)

    save_tunnel_config(cfg_dir, TunnelConfig("id-1", "pc", "one.example.com", False))
    atomic = [c for c in replace_calls if Path(c[1]).name == "tunnel.json"]
    assert atomic, "save_tunnel_config must publish via os.replace"
    src, dst = atomic[0]
    assert src.parent == cfg_dir
    assert src.name.endswith(".tmp")
    assert not list(cfg_dir.glob("*.tmp"))
    assert load_tunnel_config(cfg_dir) == TunnelConfig(
        "id-1", "pc", "one.example.com", False
    )

    save_tunnel_config(cfg_dir, TunnelConfig("id-2", "pc", "two.example.com", True))
    assert not list(cfg_dir.glob("*.tmp"))
    assert load_tunnel_config(cfg_dir) == TunnelConfig(
        "id-2", "pc", "two.example.com", True
    )

    errors = []

    def worker(i: int) -> None:
        try:
            save_tunnel_config(
                cfg_dir,
                TunnelConfig(f"tid-{i}", "pc", f"h{i}.example.com", bool(i % 2)),
            )
        except Exception as exc:  # pragma: no cover - failure path
            errors.append(exc)

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(20)]
    for t in threads:
        t.start()
    for t in threads:
        t.join(timeout=10)
    assert not errors
    assert not list(cfg_dir.glob("*.tmp"))
    data = json.loads((cfg_dir / "tunnel.json").read_text(encoding="utf-8"))
    assert data["tunnel_name"] == "pc"
    assert str(data["tunnel_id"]).startswith("tid-")


def test_create_already_exists_relists_and_recovers(tmp_path):
    counter = tmp_path / "list_count"
    body = (
        'if sys.argv[1:3] == ["tunnel", "list"]:\n'
        "    n = 0\n"
        "    try:\n"
        f"        n = int(open({str(counter)!r}).read())\n"
        "    except Exception:\n"
        "        pass\n"
        f"    with open({str(counter)!r}, 'w') as fh:\n"
        "        fh.write(str(n + 1))\n"
        '    print("ID                                   NAME     CREATED")\n'
        '    print("------------------------------------ -------- ----------")\n'
        "    if n > 0:\n"
        '        print("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee pc       2026-01-01")\n'
        'elif sys.argv[1:3] == ["tunnel", "create"]:\n'
        "    print('tunnel with name \"pc\" already exists', file=sys.stderr)\n"
        "    sys.exit(1)\n"
        'elif sys.argv[1:3] == ["tunnel", "route"]:\n'
        '    print("Updated pc.example.com route")\n'
        "else:\n"
        "    time.sleep(60)\n"
    )
    shim = logged_shim(tmp_path, body)
    cert = tmp_path / "cert.pem"
    cert.write_text("cert", encoding="utf-8")
    cfg_dir = tmp_path / "cfg"
    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=cfg_dir,
        cert_path=cert,
    )

    assert mgr.start_named("pc", "pc.example.com", 8765) is True
    assert mgr.state is TunnelState.RUNNING
    cfg = load_tunnel_config(cfg_dir)
    assert cfg.tunnel_id == "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    calls = wait_for_calls(tmp_path, "tunnel run --config")
    assert calls.count("tunnel list") == 2
    assert calls.count("tunnel create") == 1

    mgr.stop()


def test_docs_url_printed_before_tunnel_url_does_not_latch(tmp_path):
    shim = write_shim(
        tmp_path,
        "import time\n"
        "print('see https://developers.cloudflare.com/docs first', flush=True)\n"
        "time.sleep(0.2)\n"
        "print('https://fast-red-xyz.trycloudflare.com', flush=True)\n"
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
    assert urls == ["https://fast-red-xyz.trycloudflare.com"]
    time.sleep(0.5)
    assert mgr.url == "https://fast-red-xyz.trycloudflare.com"

    mgr.stop()


def test_ctor_and_added_listeners_both_fire(tmp_path):
    shim = write_shim(
        tmp_path,
        "print('https://fanout-test.trycloudflare.com', flush=True)\n"
        "import time\n"
        "time.sleep(60)\n",
    )
    ctor_urls, added_urls = [], []
    ctor_states, added_states = [], []
    url_event = threading.Event()
    state_event = threading.Event()

    def ctor_on_state(state: TunnelState) -> None:
        ctor_states.append(state)
        if state is TunnelState.RUNNING:
            state_event.set()

    mgr = TunnelManager(
        cloudflared_finder=lambda: shim,
        config_dir=tmp_path / "cfg",
        on_state=ctor_on_state,
        on_url=lambda url: (ctor_urls.append(url), url_event.set()),
    )
    mgr.add_state_listener(added_states.append)
    mgr.add_url_listener(added_urls.append)

    assert mgr.start_quick(8765) is True
    assert state_event.wait(timeout=5)
    assert url_event.wait(timeout=5)
    # (a) url fan-out: ctor subscriber AND added subscriber both fired
    assert ctor_urls == ["https://fanout-test.trycloudflare.com"]
    assert added_urls == ["https://fanout-test.trycloudflare.com"]
    # (b) state fan-out: both saw RUNNING, both see OFF on stop
    assert TunnelState.RUNNING in ctor_states
    assert TunnelState.RUNNING in added_states
    mgr.stop()
    assert TunnelState.OFF in ctor_states
    assert TunnelState.OFF in added_states
