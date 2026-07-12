package com.sbi.surakshasathi.feature.apkscan.data.branding

/**
 * Known-good SBI/YONO package identities (§5 Tier 1.3). Any package whose
 * label/name LOOKS like SBI/YONO but isn't signed by one of these
 * certificates — or isn't one of these package names at all — is flagged
 * `isImpersonation = true` immediately, on-device, with no network call.
 *
 * This bundled default ships in the client so impersonation detection works
 * offline from install. In production this list SHOULD also be refreshable
 * from the backend (`GET /threat/sbi-allowlist`) and cached — same pattern as
 * Flow 5's official-link allow-list — but the bundled default is authoritative
 * for anything not yet synced, since a stale-but-present allow-list beats none.
 */
object SbiAllowList {
    /** Package name → expected SHA-256 signing certificate fingerprint(s). */
    val OFFICIAL_PACKAGES: Map<String, Set<String>> =
        mapOf(
            // Real YONO SBI package name; fingerprint is a placeholder until the
            // real SBI signing cert hash is supplied — see README "known limitations".
            "com.sbi.lotusintouch" to
                setOf(
                    "REPLACE_WITH_REAL_SBI_YONO_SIGNING_CERT_SHA256",
                ),
            "com.sbi.SBIFreedomPlus" to
                setOf(
                    "REPLACE_WITH_REAL_SBI_FREEDOM_SIGNING_CERT_SHA256",
                ),
        )

    /**
     * Words/brand tokens that make a package LOOK like it's SBI/YONO —
     * matched case-insensitively against the app label and package name.
     */
    val BRAND_TOKENS = listOf("sbi", "yono", "state bank", "statebank")

    fun isOfficialPackageName(packageName: String): Boolean = OFFICIAL_PACKAGES.containsKey(packageName)

    fun expectedCertsFor(packageName: String): Set<String> = OFFICIAL_PACKAGES[packageName].orEmpty()

    fun looksLikeSbiBranding(
        packageName: String,
        appLabel: String,
    ): Boolean {
        val haystack = "$packageName $appLabel".lowercase()
        return BRAND_TOKENS.any { haystack.contains(it) }
    }
}
