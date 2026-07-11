"""Central runtime configuration. All external credentials/flags load from env (.env locally,
GCP Secret Manager -> env injection in Cloud Run). Nothing here should be hardcoded."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Blue DB + guidelines vector store, consolidated onto one Cloud SQL for PostgreSQL
    # instance (pgvector extension) -- GCP-native, avoids a cross-cloud hop to Atlas/Pinecone,
    # and one instance is enough at this corpus/message scale. See docs/DATASTORE_CHOICES.md.
    postgres_dsn: str = ""          # postgresql://user:pass@/dbname?host=/cloudsql/<instance-conn-name>
    pgvector_dim: int = 96          # must match clustering.embedding's reduced dim (text + region + persona + message_type)

    # Cache / bloom filter -- Memorystore for Redis (GCP-native managed Redis)
    redis_url: str = ""

    # LLM
    groq_api_key: str = ""
    groq_model: str = "llama-3.3-70b-versatile"

    # Reputation
    virustotal_api_key: str = ""

    # Language (Google Cloud Translation v2 REST API, key-based auth -- simpler than a service
    # account for this single API call, and matches the credential type actually available)
    google_translate_api_key: str = ""
    bhashini_api_key: str = ""
    enable_bhashini_fallback: bool = False

    # Callback verification
    twilio_account_sid: str = ""
    twilio_auth_token: str = ""

    # URL reputation
    whoisxml_api_key: str = ""

    # Web corroboration
    tavily_api_key: str = ""

    # Push
    firebase_credentials_path: str = ""

    # Feature flags
    enable_truecaller: bool = False

    # Latency budgets (ms)
    web_search_timeout_ms: int = 1200
    url_head_timeout_ms: int = 800

    # Clustering -- per your instruction, region + persona + message type should be the primary
    # clustering signal, with text embedding as a secondary refinement within those buckets. All
    # three weight multipliers are deliberately larger than 1.0 so they dominate the raw text-PCA
    # distance rather than being drowned out by it.
    embedding_dim_raw: int = 384
    embedding_dim_reduced: int = 96   # 74 text-PCA + 2 region + 6 persona (one-hot) + 14 message_type (one-hot)
    region_weight_multiplier: float = 2.5
    persona_weight_multiplier: float = 2.0
    message_type_weight_multiplier: float = 3.0
    denstream_core_radius: float = 0.35
    denstream_outlier_promote_count: int = 5


settings = Settings()
