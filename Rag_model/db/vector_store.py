"""Guidelines vector store operations (pgvector). Replaces the Pinecone `guidelines` namespace
from the original design -- same role (semantic retrieval of fraud-education reference docs for
the LLM prompt), backed by the same Cloud SQL instance as Blue DB.
"""

from datetime import datetime, timezone

from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from db.postgres_client import GuidelineDoc


def upsert_guideline_chunks(session: Session, chunks: list[dict], batch_size: int = 500) -> None:
    """chunks: [{"doc_id": ..., "source_file": ..., "chunk_text": ..., "embedding": [...] }, ...].
    One bulk INSERT ... ON CONFLICT per batch instead of a per-row ORM merge -- merge() does a
    round-trip SELECT-then-INSERT/UPDATE per row, which over a network connection to Cloud SQL
    (rather than localhost) turned thousands of rows into thousands of round-trips and was the
    actual bottleneck the first time this was run."""
    now = datetime.now(timezone.utc)
    rows = [
        {
            "doc_id": c["doc_id"],
            "source_file": c["source_file"],
            "chunk_text": c["chunk_text"],
            "embedding": c["embedding"],
            "ingested_at": now,
        }
        for c in chunks
    ]

    for i in range(0, len(rows), batch_size):
        batch = rows[i : i + batch_size]
        stmt = pg_insert(GuidelineDoc).values(batch)
        stmt = stmt.on_conflict_do_update(
            index_elements=["doc_id"],
            set_={
                "chunk_text": stmt.excluded.chunk_text,
                "embedding": stmt.excluded.embedding,
                "ingested_at": stmt.excluded.ingested_at,
            },
        )
        session.execute(stmt)
        session.commit()


def semantic_search(session: Session, query_embedding: list[float], top_k: int = 5) -> list[GuidelineDoc]:
    """Nearest-neighbour search via pgvector's cosine-distance operator (<=>)."""
    return (
        session.query(GuidelineDoc)
        .order_by(GuidelineDoc.embedding.cosine_distance(query_embedding))
        .limit(top_k)
        .all()
    )
