"""A small Gemini client, standard library only, with the same endpoint and headers as production."""

import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
RETRY_WAITS = (15, 30, 60)
BUSY = {429, 500, 503}


class QuotaExhausted(Exception):
    """The daily quota is gone: retrying now cannot help, so the run stops and keeps its cache."""


class GeminiError(Exception):
    pass


def api_key():
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if key:
        return key
    env_file = Path(__file__).resolve().parent.parent / ".env"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            if line.startswith("GEMINI_API_KEY="):
                return line.split("=", 1)[1].strip().strip("\"'")
    return ""


def _post(model, body, key, timeout):
    request = urllib.request.Request(
        f"{BASE_URL}/models/{model}:generateContent",
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", "x-goog-api-key": key},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def generate(model, body, key, timeout=60):
    """Returns {"text", "seconds", "finish_reason"}. Retries only what a wait can fix."""
    for wait in (*RETRY_WAITS, None):
        started = time.monotonic()
        try:
            payload = _post(model, body, key, timeout)
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:500]
            if e.code == 429 and "PerDay" in detail:
                raise QuotaExhausted(detail) from e
            if e.code not in BUSY or wait is None:
                raise GeminiError(f"HTTP {e.code}: {detail}") from e
        except (TimeoutError, urllib.error.URLError) as e:
            if wait is None:
                raise GeminiError(f"request failed: {e}") from e
        else:
            candidate = (payload.get("candidates") or [{}])[0]
            parts = candidate.get("content", {}).get("parts") or [{}]
            return {
                # Production reads the first part too, so a stray part counts against the model.
                "text": parts[0].get("text", ""),
                "seconds": round(time.monotonic() - started, 2),
                "finish_reason": candidate.get("finishReason", "UNKNOWN"),
            }
        time.sleep(wait)
    raise GeminiError("retries exhausted")
