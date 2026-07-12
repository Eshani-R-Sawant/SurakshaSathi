package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.core.common.Result
import com.sbi.surakshasathi.core.common.safeCall
import com.sbi.surakshasathi.feature.apkscan.data.branding.SbiAllowList
import com.sbi.surakshasathi.feature.apkscan.domain.model.ApkVerdict
import com.sbi.surakshasathi.feature.apkscan.domain.repository.UrlReputationRepository
import com.sbi.surakshasathi.feature.awareness.domain.model.OfficialLinkCheckResult
import com.sbi.surakshasathi.feature.awareness.domain.model.OfficialLinkVerdict
import com.sbi.surakshasathi.feature.awareness.domain.repository.OfficialLinkVerifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Is this the real SBI app?" (§7c 5.1) — the highest-value, most concrete
 * feature in Flow 5. Reuses Flow 2's [SbiAllowList] (package/signing-cert
 * allow-list) and [UrlReputationRepository] (local cache + cloud reputation)
 * rather than duplicating that logic.
 */
@Singleton
class OfficialLinkVerifierImpl
    @Inject
    constructor(
        private val urlReputationRepository: UrlReputationRepository,
    ) : OfficialLinkVerifier {
        override suspend fun verify(scannedContent: String): Result<OfficialLinkCheckResult> =
            safeCall {
                val content = scannedContent.trim()

                when {
                    SbiAllowList.isOfficialPackageName(content) ->
                        OfficialLinkCheckResult(
                            content,
                            OfficialLinkVerdict.VERIFIED_OFFICIAL,
                            "This is SBI's official package identity.",
                        )
                    OFFICIAL_DOMAINS.any { content.contains(it, ignoreCase = true) } ->
                        OfficialLinkCheckResult(
                            content,
                            OfficialLinkVerdict.VERIFIED_OFFICIAL,
                            "This links to an official SBI domain.",
                        )
                    KNOWN_FAKE_MARKERS.any { content.contains(it, ignoreCase = true) } ->
                        OfficialLinkCheckResult(
                            content,
                            OfficialLinkVerdict.KNOWN_FAKE,
                            "This matches a known fake-SBI naming pattern.",
                        )
                    content.startsWith("http", ignoreCase = true) || content.contains(".") -> {
                        val urlResult = urlReputationRepository.checkUrl(content)
                        val verdict = (urlResult as? Result.Success)?.data?.verdict
                        when (verdict) {
                            ApkVerdict.MALWARE ->
                                OfficialLinkCheckResult(
                                    content,
                                    OfficialLinkVerdict.KNOWN_FAKE,
                                    "This domain is flagged as malicious.",
                                )
                            ApkVerdict.GOODWARE ->
                                OfficialLinkCheckResult(
                                    content,
                                    OfficialLinkVerdict.UNKNOWN,
                                    "Not a recognized SBI domain, but not flagged as malicious either. Verify independently before entering any details.",
                                )
                            else ->
                                OfficialLinkCheckResult(
                                    content,
                                    OfficialLinkVerdict.UNKNOWN,
                                    "Could not verify this against SBI's official identities.",
                                )
                        }
                    }
                    else ->
                        OfficialLinkCheckResult(
                            content,
                            OfficialLinkVerdict.UNKNOWN,
                            "Could not verify this against SBI's official identities.",
                        )
                }
            }

        private companion object {
            val OFFICIAL_DOMAINS = listOf("sbi.co.in", "onlinesbi.sbi", "yonosbi.com")
            val KNOWN_FAKE_MARKERS = listOf("sbi-yono", "yono-sbi-update", "sbi.verify", "sbi-kyc")
        }
    }
