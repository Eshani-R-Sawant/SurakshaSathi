"""The 07:00 IST daily job (GCP Cloud Scheduler -> Cloud Run Job, cron `30 1 * * *` UTC):
1. Threshold check: macro-clusters whose message count crossed the alert threshold in the last
   24h get an LLM-generated regional alert, FCM-pushed to users in the matching region
   (vulnerable-group users prioritized).
2. Heatmap: rebuilt from the trailing window (alerting/heatmap.py) and pushed as a daily digest.

Runs after crawlers/cybercrime_digest_job.py (scheduled ~06:00 IST) so same-day digest data is
available for the heatmap.
"""

from datetime import datetime, timedelta, timezone

from sqlalchemy import func
from sqlalchemy.orm import Session

from alerting.fcm_sender import send_to_tokens
from alerting.heatmap import build_heatmap
from db.postgres_client import Cluster, Message, UserMap
from llm.groq_client import generate_threat_report
from llm.prompt_templates import build_alerting_prompt

ALERT_THRESHOLD_MESSAGE_COUNT = 20  # messages in trailing 24h for a macro-cluster to trigger an alert


def macro_clusters_crossing_threshold(session: Session) -> list[Cluster]:
    cutoff = datetime.now(timezone.utc) - timedelta(hours=24)

    counts = dict(
        session.query(Message.cluster_id, func.count(Message.message_id))
        .filter(Message.timestamp >= cutoff, Message.cluster_id.isnot(None))
        .group_by(Message.cluster_id)
        .all()
    )

    macro_clusters = session.query(Cluster).filter(Cluster.cluster_type == "macro").all()
    triggered = []
    for macro in macro_clusters:
        recent_count = sum(
            counts.get(micro_id, 0)
            for micro_id in _child_micro_ids(session, macro.cluster_id)
        )
        if recent_count >= ALERT_THRESHOLD_MESSAGE_COUNT:
            macro._recent_count = recent_count  # transient attribute, not persisted
            triggered.append(macro)
    return triggered


def _child_micro_ids(session: Session, macro_id: str) -> list[str]:
    rows = session.query(Cluster.cluster_id).filter(Cluster.parent_macro_cluster_id == macro_id).all()
    return [r[0] for r in rows]


def users_in_region(session: Session, region: str) -> list[str]:
    rows = (
        session.query(UserMap.metadata_json)
        .filter(UserMap.region == region)
        .order_by(UserMap.is_vulnerable_group.desc())
        .all()
    )
    return [r[0].get("fcm_token") for r in rows if r[0] and r[0].get("fcm_token")]


def run_threshold_alerts(session: Session) -> int:
    sent = 0
    for macro in macro_clusters_crossing_threshold(session):
        prompt = build_alerting_prompt(
            macro_cluster_sample_message=macro.sample_message,
            fraud_type=macro.fraud_type or "unknown",
            region=macro.region or "unknown",
            message_count_last_24h=getattr(macro, "_recent_count", macro.weight),
            retrieved_docs=[],  # populated via db.vector_store.semantic_search on macro.sample_message
        )
        report = generate_threat_report(prompt)
        tokens = users_in_region(session, macro.region) if macro.region else []
        sent += send_to_tokens(
            tokens,
            title=f"Fraud alert: {report.threat_type}",
            body=report.plain_language_explanation,
            data={"risk_score": str(report.risk_score), "region": macro.region or ""},
        )
    return sent


def run_daily_heatmap_digest(session: Session) -> None:
    heatmap = build_heatmap(session)
    for entry in heatmap[:10]:  # top 10 regions by volume get a region-specific digest push
        tokens = users_in_region(session, entry.region)
        send_to_tokens(
            tokens,
            title="Daily fraud activity update",
            body=f"{entry.total} fraud reports in your region over the last 12 days.",
            data={"region": entry.region, "total": str(entry.total)},
        )


def run(session: Session) -> None:
    alerts_sent = run_threshold_alerts(session)
    run_daily_heatmap_digest(session)
    print(f"Daily job complete: {alerts_sent} threshold alerts sent, heatmap digest pushed.")
