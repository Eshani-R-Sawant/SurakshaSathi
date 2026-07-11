"""Vernacular -> English translation. Primary: Google Cloud Translation v2 REST API (key-based
auth -- what we actually have a credential for). Bhashini NMT would be the Indian-language-first
alternative, but no Bhashini API key has been provisioned yet, so it's wired as an optional
fallback behind ENABLE_BHASHINI_FALLBACK, defaulting off. Flip that flag + set BHASHINI_API_KEY
once you register at bhashini.gov.in/ulca (free) -- no code change needed elsewhere.
"""

import httpx

from common.resilience import with_retry
from config.settings import settings

TRANSLATE_V2_ENDPOINT = "https://translation.googleapis.com/language/translate/v2"


@with_retry("google_translate", exceptions=(httpx.HTTPError,))
def _call_google_translate(text: str, source_language: str | None) -> str:
    params = {"key": settings.google_translate_api_key, "target": "en", "q": text}
    if source_language and source_language not in ("unknown", "mixed_script"):
        params["source"] = source_language
    resp = httpx.post(TRANSLATE_V2_ENDPOINT, params=params, timeout=settings.url_head_timeout_ms / 1000)
    resp.raise_for_status()
    return resp.json()["data"]["translations"][0]["translatedText"]


def _call_bhashini(text: str, source_language: str) -> str:
    # Bhashini's pipeline API requires a two-step call (pipeline config -> compute) with a task
    # sequence of `translation`. Left as a documented stub until BHASHINI_API_KEY is available;
    # signature matches _call_google_translate so translate_to_english() can swap silently.
    raise NotImplementedError("Bhashini fallback not wired -- BHASHINI_API_KEY not set")


def translate_to_english(text: str, source_language: str) -> str:
    if source_language == "en":
        return text
    if not settings.google_translate_api_key:
        raise RuntimeError("GOOGLE_TRANSLATE_API_KEY not set -- see .env.example")
    try:
        return _call_google_translate(text, source_language)
    except Exception:
        if settings.enable_bhashini_fallback:
            return _call_bhashini(text, source_language)
        raise
