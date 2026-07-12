package com.sbi.surakshasathi.feature.ncrpreport.domain.model

/**
 * Forensic packet submitted to I4C/NCRP (§7b). Mirrors the JSON contract:
 * ```json
 * {
 *   "apkSha256": "...", "installSource": "...",
 *   "deviceIntegrity": { "status": "...", "rooted": false },
 *   "offendingMessage": { "sender": "...", "body": "...", "urls": ["..."] },
 *   "location": { "region": "...", "lat": 0.0, "lng": 0.0 },
 *   "reportedAtMillis": 0, "reporterConsent": true
 * }
 * ```
 * Every field is optional except consent+timestamp — a report can originate
 * from a flagged message, a malicious APK, or both.
 */
data class ForensicReport(
    val apkSha256: String? = null,
    val apkPackageName: String? = null,
    val installSource: String? = null,
    val deviceIntegrityStatus: String? = null,
    val deviceRooted: Boolean = false,
    val offendingSender: String? = null,
    val offendingMessageBody: String? = null,
    val offendingUrls: List<String> = emptyList(),
    val regionLabel: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val reportedAtMillis: Long,
    val reporterConsent: Boolean,
)

enum class NcrpReportStatus {
    /** Submitted successfully; [NcrpCaseResult.caseId] is the real I4C case ID. */
    REGISTERED,

    /** Submission failed (offline/backend down); a provisional local ID was issued and a retry is queued. */
    PENDING_SYNC,

    /** All retries exhausted — surfaced to the user so they can retry manually. */
    FAILED,
}

data class NcrpCaseResult(
    val caseId: String,
    val status: NcrpReportStatus,
    val isProvisional: Boolean,
)
