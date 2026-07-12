package com.sbi.surakshasathi.feature.messagescan.data.classifier

import javax.inject.Inject

/**
 * Interface for validating whether a sender ID is a TRAI DLT-registered sender.
 *
 * TRAI (Telecom Regulatory Authority of India) mandates all commercial SMS
 * senders register on the Distributed Ledger Technology (DLT) platform.
 * Legitimate SBI SMS headers start with specific registered prefixes
 * (e.g. "SBI-" series, "1600" series).
 *
 * Mockable for testing: [TraiDltValidatorImpl] is the production implementation.
 */
interface TraiDltValidator {
    /**
     * Returns true if [senderId] matches a registered SBI/bank DLT header.
     * A false result is a signal (not a conclusive verdict) of spoofing.
     */
    fun isRegisteredSbiSender(senderId: String): Boolean
}

/**
 * Production implementation.
 *
 * In production: this should query the TRAI DLT API or a cached copy of the
 * registered sender database. For Phase 1, we use a bundled allow-list of
 * known SBI sender IDs. Phase 2 enhancement: cache from backend.
 *
 * TRAI DLT reference: https://www.trai.gov.in/dlt
 */
class TraiDltValidatorImpl
    @Inject
    constructor() : TraiDltValidator {
        override fun isRegisteredSbiSender(senderId: String): Boolean {
            val normalized = senderId.uppercase().trim()
            // EXACT match only — DLT-registered headers are a fixed, finite set of
            // strings, not a prefix family. `startsWith` matching here was a real
            // bug: "SBIYONO1" (a known-fake sender) starts with the legitimate
            // "SBIYONO"/"SBI" prefixes, so prefix matching would have incorrectly
            // validated it — and would do the same for ANY "SBI*"-prefixed spoof,
            // silently defeating the whole point of DLT validation.
            return REGISTERED_SBI_SENDERS.contains(normalized)
        }

        companion object {
            /**
             * Exact registered SBI DLT sender IDs.
             * Source: SBI's official TRAI DLT registration (update as SBI adds new headers).
             */
            private val REGISTERED_SBI_SENDERS =
                setOf(
                    "SBI",
                    "SBICRD",
                    "SBIBNK",
                    "SBIYONO",
                    "YONO",
                    "SBINBT",
                    "SBIOTP",
                    "VM-SBI",
                    "BW-SBI",
                    "AX-SBI",
                    "JK-SBI",
                    "SBI-OTP",
                    "SBI-ALERTS",
                )

            /**
             * Known fake/spoofed sender IDs used in SBI phishing campaigns.
             * These should never produce a valid classification.
             */
            val KNOWN_FAKE_SENDERS =
                setOf(
                    "SB1", // Zero vs letter confusion
                    "5BI",
                    "SBII",
                    "SBIYONO1",
                )
        }
    }
