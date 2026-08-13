import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import app as app_module  # noqa: E402
from app import build_app  # noqa: E402


@pytest.fixture(autouse=True)
def no_presentmon_binary(monkeypatch):
    monkeypatch.setattr(app_module, "_presentmon_path", lambda: "/nonexistent/PresentMon64.exe")


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


def test_ws_accepts_clients_without_origin():
    app = build_app(simulate=True)
    with TestClient(app) as client:
        with client.websocket_connect("/ws") as ws:
            welcome = ws.receive_json()
            assert welcome["type"] == "welcome"


def test_ws_rejects_browser_origin():
    app = build_app(simulate=True)
    with TestClient(app) as client:
        with pytest.raises(WebSocketDisconnect) as excinfo:
            with client.websocket_connect("/ws", headers={"Origin": "https://evil.example"}):
                pass
        assert excinfo.value.code == 1008


def test_ws_with_token_accepts_correct_token():
    app = build_app(simulate=True, token="sekret")
    with TestClient(app) as client:
        with client.websocket_connect("/ws") as ws:
            ws.send_json({"type": "auth", "token": "sekret"})
            welcome = ws.receive_json()
            assert welcome["type"] == "welcome"


def test_ws_with_token_rejects_wrong_token():
    app = build_app(simulate=True, token="sekret")
    with TestClient(app) as client:
        with pytest.raises(WebSocketDisconnect) as excinfo:
            with client.websocket_connect("/ws") as ws:
                ws.send_json({"type": "auth", "token": "wrong"})
                ws.receive_json()
        assert excinfo.value.code == 1008


def test_ws_with_token_times_out_without_auth():
    app = build_app(simulate=True, token="sekret", auth_timeout=0.1)
    with TestClient(app) as client:
        with pytest.raises(WebSocketDisconnect) as excinfo:
            with client.websocket_connect("/ws") as ws:
                ws.receive_json()
        assert excinfo.value.code == 1008
