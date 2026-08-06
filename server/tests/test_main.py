import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from main import build_app  # noqa: E402


def test_build_app_http_source_uses_http_adapter():
    app = build_app(source="http")
    assert app.state.welcome.source == "librehardwaremonitor"


def test_build_app_auto_falls_back_to_http_without_dll():
    app = build_app(source="auto")
    assert app.state.welcome.source in ("lhm-lib", "librehardwaremonitor")


def test_build_app_lib_source_requests_dll():
    app = build_app(source="lib")
    assert app.state.welcome.source == "lhm-lib"


def test_build_app_simulate_uses_simulator():
    app = build_app(simulate=True)
    assert app.state.welcome.source == "simulator"


def test_build_app_accepts_fps_process_arg():
    app = build_app(simulate=True, fps_process="game.exe")
    assert app.state.fps_process == "game.exe"
