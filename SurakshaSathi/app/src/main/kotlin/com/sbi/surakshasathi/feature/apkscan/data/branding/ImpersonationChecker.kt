package com.sbi.surakshasathi.feature.apkscan.data.branding

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1.3 (§5) — the core hackathon detection: a package that *looks* like
 * YONO but is signed by a non-SBI certificate is impersonation, full stop,
 * decided on-device with zero network calls.
 */
@Singleton
class ImpersonationChecker
    @Inject
    constructor() {
        /**
         * @param signingCertSha256 fingerprint(s) of the package actually being scanned.
         */
        fun check(
            packageName: String,
            appLabel: String,
            signingCertSha256: List<String>,
        ): Boolean {
            if (SbiAllowList.isOfficialPackageName(packageName)) {
                // Claims to be an official package name — its cert MUST match.
                val expected = SbiAllowList.expectedCertsFor(packageName)
                return signingCertSha256.none { it in expected }
            }

            // Not the official package name, but branded like SBI/YONO — always impersonation.
            return SbiAllowList.looksLikeSbiBranding(packageName, appLabel)
        }
    }
