package com.sbi.surakshasathi.feature.frauddashboard.domain.model

/** One aggregated, anonymized fraud-report cluster point on the heatmap (§7). */
data class FraudCluster(
    val lat: Double,
    val lng: Double,
    /** Relative intensity, 0.0–1.0 — drives heatmap color/radius. */
    val weight: Float,
    val persona: String,
    val region: String,
    val campaignTag: String,
)

/** A pushable target segment — region + persona + language triple maps to an FCM topic (e.g. farmer_vidarbha_mr). */
data class UserSegment(
    val region: String,
    val persona: String,
    val language: String,
) {
    /** FCM topic name this segment maps to. */
    fun toFcmTopic(): String = "${persona.lowercase()}_${region.lowercase().replace(" ", "_")}_$language"
}
