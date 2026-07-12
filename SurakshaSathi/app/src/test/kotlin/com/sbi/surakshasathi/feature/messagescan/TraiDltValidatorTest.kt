package com.sbi.surakshasathi.feature.messagescan

import com.sbi.surakshasathi.feature.messagescan.data.classifier.TraiDltValidatorImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TraiDltValidatorTest {

    private val validator = TraiDltValidatorImpl()

    @Test
    fun `test valid registered SBI prefixes`() {
        assertTrue(validator.isRegisteredSbiSender("SBIOTP"))
        assertTrue(validator.isRegisteredSbiSender("SBIYONO"))
        assertTrue(validator.isRegisteredSbiSender("SBINBT"))
        assertTrue(validator.isRegisteredSbiSender("VM-SBI"))
        assertTrue(validator.isRegisteredSbiSender("BW-SBI"))
    }

    @Test
    fun `test exact match registers`() {
        assertTrue(validator.isRegisteredSbiSender("SBI"))
        assertTrue(validator.isRegisteredSbiSender("SBI-OTP"))
    }

    @Test
    fun `test invalid or fake senders`() {
        assertFalse(validator.isRegisteredSbiSender("SB1")) // Number 1 instead of I
        assertFalse(validator.isRegisteredSbiSender("5BI")) // Number 5 instead of S
        assertFalse(validator.isRegisteredSbiSender("SBIYONO1"))
        assertFalse(validator.isRegisteredSbiSender("HDFCBK"))
        assertFalse(validator.isRegisteredSbiSender("AD-ALERT"))
    }

    @Test
    fun `regression - prefix matching must not let 'SBI-prefixed' spoofs through`() {
        // Real bug found and fixed: isRegisteredSbiSender used to do
        // `normalized.startsWith(prefix)` against bare prefixes like "SBI"
        // and "SBIYONO", so ANY sender starting with those strings —
        // including spoofs — validated as "registered". Matching must be
        // exact against the known sender-ID set.
        assertFalse(validator.isRegisteredSbiSender("SBIFRAUD"))
        assertFalse(validator.isRegisteredSbiSender("SBIYONOX"))
        assertFalse(validator.isRegisteredSbiSender("SBI999"))
    }
}
