"""Groq API wrapper (Llama-3.3-70B) with JSON structured output. Both Output 1 (per-message) and
Output 2 (alerting) go through this single function -- only the prompt differs.

NOTE: Groq's `json_schema` strict structured-output mode is only supported on a handful of
models (see https://console.groq.com/docs/structured-outputs#supported-models) -- llama-3.3-70b-
versatile is NOT one of them (confirmed by a live 400 from the API: "This model does not support
response format `json_schema`"). Falls back to `json_object` mode (guarantees valid JSON syntax,
not schema conformance) with the schema spelled out in the system prompt, then validates with
pydantic and retries once on a validation failure -- the practical equivalent for models without
native schema enforcement.
"""

import json

from groq import Groq
from pydantic import ValidationError

from common.resilience import with_retry
from common.schemas import ThreatReport
from config.settings import settings
from llm.prompt_templates import SYSTEM_PROMPT
from llm.schema import THREAT_REPORT_SCHEMA

_client: Groq | None = None

SCHEMA_INSTRUCTION = (
    "\n\nRespond with ONLY a JSON object matching exactly this schema (no prose, no markdown "
    f"fences):\n{json.dumps(THREAT_REPORT_SCHEMA['schema'], indent=2)}"
)


def _get_client() -> Groq:
    global _client
    if _client is None:
        if not settings.groq_api_key:
            raise RuntimeError("GROQ_API_KEY not set -- see .env.example")
        _client = Groq(api_key=settings.groq_api_key)
    return _client


@with_retry("groq", exceptions=(Exception,))
def _call_groq(user_prompt: str) -> str:
    client = _get_client()
    completion = client.chat.completions.create(
        model=settings.groq_model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT + SCHEMA_INSTRUCTION},
            {"role": "user", "content": user_prompt},
        ],
        response_format={"type": "json_object"},
        temperature=0.2,
    )
    return completion.choices[0].message.content


def generate_threat_report(user_prompt: str) -> ThreatReport:
    raw_json = _call_groq(user_prompt)
    try:
        return ThreatReport.model_validate(json.loads(raw_json))
    except (json.JSONDecodeError, ValidationError):
        # one retry: the model occasionally drifts from the schema in json_object mode (no
        # native enforcement) -- a second attempt is cheap and usually self-corrects
        raw_json = _call_groq(user_prompt)
        return ThreatReport.model_validate(json.loads(raw_json))
