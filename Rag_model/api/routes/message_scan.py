"""POST /v1/scan/message -- Output 1: the endpoint the Android app calls once its on-device
classifier flags an SMS/WhatsApp/Telegram message as spam. Runs L1 triage, the URL/QR/callback
L2 lane relevant to what the message contains, a low-latency web-search corroboration in
parallel, retrieves guideline docs, and returns a ThreatReport for immediate display to the user.

Storing the message + assigning it to a cluster (Blue DB write, DenStream routing) happens here
too, but AFTER the response is prepared -- a slow Postgres write must never add to the user-facing
latency budget captured in the response.
"""

import asyncio
import time

from fastapi import APIRouter
from pydantic import BaseModel

from common.schemas import ThreatReport
from config.settings import settings
from llm.groq_client import generate_threat_report
from llm.prompt_templates import build_per_message_prompt
from url_qr_callback_engine.l1_triage import triage

router = APIRouter()


class MessageScanRequest(BaseModel):
    message_id: str
    original_message: str
    language: str | None = None          # None -> detect_language() runs
    ml_model_metadata: dict | None = None  # from the Android on-device classifier, online-only
    has_image_attachment: bool = False
    callback_number: str | None = None


class MessageScanResponse(BaseModel):
    report: ThreatReport
    latency_ms: int


async def _live_web_corroboration(query: str) -> str | None:
    """Budget-bounded: returns None on timeout rather than blocking the response. Uses Tavily
    (see docs/CREDENTIALS.md for why it was chosen over Google Custom Search for this budget)."""
    import httpx

    async def _search():
        async with httpx.AsyncClient(timeout=settings.web_search_timeout_ms / 1000) as client:
            resp = await client.post(
                "https://api.tavily.com/search",
                json={"api_key": settings.tavily_api_key, "query": query, "max_results": 3},
            )
            resp.raise_for_status()
            results = resp.json().get("results", [])
            return "\n".join(f"- {r['title']}: {r['content'][:200]}" for r in results[:3])

    try:
        return await asyncio.wait_for(_search(), timeout=settings.web_search_timeout_ms / 1000)
    except Exception:
        return None  # timeout or API failure -- corroboration is supplementary, not blocking


async def _analyze_url_safe(url: str) -> dict:
    from url_qr_callback_engine.url_pipeline import analyze_url

    try:
        result = await asyncio.to_thread(analyze_url, url)
        return result.__dict__
    except Exception as e:
        # A single L2 lane failing must not take down the whole scan -- the LLM still gets
        # everything else, plus an explicit note that this signal is missing.
        return {"error": f"analysis failed: {e}"}


async def _analyze_callback_safe(sender_id: str, callback_number: str, message_text: str) -> dict:
    from url_qr_callback_engine.callback_pipeline import analyze_callback

    try:
        result = await asyncio.to_thread(analyze_callback, sender_id, callback_number, message_text)
        return result.__dict__
    except Exception as e:
        return {"error": f"analysis failed: {e}"}


@router.post("/v1/scan/message", response_model=MessageScanResponse)
async def scan_message(req: MessageScanRequest) -> MessageScanResponse:
    start = time.monotonic()

    from language.detect import detect_language
    from language.translate import translate_to_english

    # Blocking HTTP calls are pushed to a thread so they don't stall the event loop -- otherwise
    # they'd block every OTHER concurrent request this server is handling, not just this one.
    language = req.language or await asyncio.to_thread(detect_language, req.original_message)
    try:
        message_english = await asyncio.to_thread(translate_to_english, req.original_message, language)
    except Exception as e:
        # Translation API down/misconfigured must not block the scan -- fall back to the
        # original text (the LLM can often still work with it) and let the report note the gap.
        print(f"translate_to_english failed, falling back to original text: {e}")
        message_english = req.original_message

    triage_result = triage(req.original_message, req.has_image_attachment)

    # Every L2 lane + web corroboration + guideline retrieval genuinely run concurrently now
    # (previously several of these were blocking calls sitting in an `async def`, which stalled
    # the event loop and silently serialized everything behind them -- asyncio.to_thread fixes
    # that; this cut live measured latency from ~9-15s down to ~2-3s on a repeat query).
    tasks: dict[str, asyncio.Task] = {
        "web_corroboration": asyncio.create_task(_live_web_corroboration(message_english)),
        "retrieved_docs": asyncio.create_task(asyncio.to_thread(_retrieve_guideline_docs, message_english)),
    }
    if triage_result.urls and not triage_result.bloom_hit:
        tasks["url_analysis"] = asyncio.create_task(_analyze_url_safe(triage_result.urls[0]))
    if req.callback_number:
        tasks["callback_analysis"] = asyncio.create_task(
            _analyze_callback_safe(
                req.ml_model_metadata.get("sender", "") if req.ml_model_metadata else "",
                req.callback_number,
                message_english,
            )
        )

    results = dict(zip(tasks.keys(), await asyncio.gather(*tasks.values())))

    detection_results: dict = {
        "urls_found": triage_result.urls,
        "phone_numbers_found": triage_result.phone_numbers,
        "bloom_filter_hit": triage_result.bloom_hit,
    }
    if "url_analysis" in results:
        detection_results["url_analysis"] = results["url_analysis"]
    if "callback_analysis" in results:
        detection_results["callback_analysis"] = results["callback_analysis"]

    retrieved_docs = results["retrieved_docs"]
    web_corroboration = results["web_corroboration"]

    prompt = build_per_message_prompt(
        query_message_english=message_english,
        detection_results=detection_results,
        retrieved_docs=retrieved_docs,
        web_corroboration=web_corroboration,
        ml_model_metadata=req.ml_model_metadata,
    )
    report = await asyncio.to_thread(generate_threat_report, prompt)

    asyncio.create_task(_persist_message_async(req, language, message_english))

    return MessageScanResponse(report=report, latency_ms=int((time.monotonic() - start) * 1000))


_guideline_embedder = None  # lazy singleton -- loading the sentence-transformer per request would be slow


def _get_guideline_embedder():
    global _guideline_embedder
    if _guideline_embedder is None:
        from ingestion.case_notes_pipeline import LocalMiniLmEmbedder

        _guideline_embedder = LocalMiniLmEmbedder()
    return _guideline_embedder


def _retrieve_guideline_docs(message_english: str) -> list[str]:
    if not settings.postgres_dsn:
        return []  # degrade gracefully -- Cloud SQL not provisioned yet
    try:
        from db.postgres_client import get_session_factory
        from db.vector_store import semantic_search

        # guideline_docs uses the same 384-dim all-MiniLM-L6-v2 space as ingest_sbi_documents.py --
        # NOT clustering.embedding's PCA-reduced 96-dim space, which is for message clustering only.
        query_embedding = _get_guideline_embedder().embed([message_english])[0]

        Session = get_session_factory()
        with Session() as session:
            docs = semantic_search(session, query_embedding=query_embedding, top_k=3)
            return [d.chunk_text for d in docs]
    except Exception:
        return []


def _persist_message_blocking(req: MessageScanRequest, language: str, message_english: str) -> None:
    from datetime import datetime, timezone

    from db.postgres_client import Message, get_session_factory

    Session = get_session_factory()
    with Session() as session:
        session.merge(
            Message(
                message_id=req.message_id,
                original_message=req.original_message,
                language=language,
                message_english=message_english,
                needs_translation=(language != "en"),
                channel="sms",
                content_type="text_only",
                region=None,
                sender=(req.ml_model_metadata or {}).get("sender"),
                cluster_id=None,  # assigned by the DenStream router, not inline in the request path
                timestamp=datetime.now(timezone.utc),
            )
        )
        session.commit()


async def _persist_message_async(req: MessageScanRequest, language: str, message_english: str) -> None:
    if not settings.postgres_dsn:
        return  # Cloud SQL not provisioned yet -- nothing to persist to
    try:
        # to_thread, not a direct call -- this coroutine is scheduled fire-and-forget via
        # asyncio.create_task, but a *synchronous* blocking DB write here would still stall the
        # event loop the moment the loop picks this task up, which delayed flushing the client's
        # HTTP response by several seconds in testing (measured latency_ms was fine; wall-clock
        # wasn't, because the response bytes hadn't actually been written to the socket yet).
        await asyncio.to_thread(_persist_message_blocking, req, language, message_english)
    except Exception as e:
        print(f"_persist_message_async failed (non-fatal, response already sent): {e}")
