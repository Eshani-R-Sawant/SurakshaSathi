package com.sbi.surakshasathi.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "surakshasathi_prefs",
)

/**
 * Centralised DataStore for user preferences and feature flags.
 *
 * Security: DataStore is encrypted via EncryptedSharedPreferences fallback.
 * For Jetpack DataStore, encryption is handled at-rest by the file system
 * encryption on modern Android (FBE, always-on from API 24+).
 * Sensitive values (tokens) are stored in EncryptedSharedPreferences instead.
 *
 * Keys (PreferencesKeys objects) ensure typo-safe access.
 */
@Singleton
class UserPreferencesDataStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dataStore = context.dataStore

        // ── Keys ───────────────────────────────────────────────────────────────────
        private object Keys {
            val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
            val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
            val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
            val NOTIFICATION_LISTENER_GRANTED = booleanPreferencesKey("nl_granted")
            val SMS_CONSENT_GRANTED = booleanPreferencesKey("sms_consent")
            val CAMERA_CONSENT_GRANTED = booleanPreferencesKey("camera_consent")
            val LOCATION_CONSENT_GRANTED = booleanPreferencesKey("location_consent")
            val ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent")
            val CONSENT_VERSION = intPreferencesKey("consent_version")
            val CONSENT_TIMESTAMP_MS = longPreferencesKey("consent_timestamp_ms")
            val USE_OFFLINE_FALLBACK = booleanPreferencesKey("use_offline_fallback")
            val FRAUD_HEATMAP_LAST_SYNC = longPreferencesKey("fraud_heatmap_last_sync")
        }

        // ── User Preferences flow ─────────────────────────────────────────────────
        val userPreferences: Flow<UserPreferences> =
            dataStore.data
                .catch { e ->
                    if (e is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }
                .map { prefs ->
                    UserPreferences(
                        selectedLanguage = prefs[Keys.SELECTED_LANGUAGE] ?: "en",
                        notificationsEnabled = prefs[Keys.NOTIFICATION_ENABLED] ?: true,
                        onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
                        notificationListenerGranted = prefs[Keys.NOTIFICATION_LISTENER_GRANTED] ?: false,
                        smsConsentGranted = prefs[Keys.SMS_CONSENT_GRANTED] ?: false,
                        cameraConsentGranted = prefs[Keys.CAMERA_CONSENT_GRANTED] ?: false,
                        locationConsentGranted = prefs[Keys.LOCATION_CONSENT_GRANTED] ?: false,
                        analyticsConsent = prefs[Keys.ANALYTICS_CONSENT] ?: false,
                        consentVersion = prefs[Keys.CONSENT_VERSION] ?: 0,
                        consentTimestampMs = prefs[Keys.CONSENT_TIMESTAMP_MS] ?: 0L,
                        useOfflineFallback = prefs[Keys.USE_OFFLINE_FALLBACK] ?: false,
                        fraudHeatmapLastSyncMs = prefs[Keys.FRAUD_HEATMAP_LAST_SYNC] ?: 0L,
                    )
                }

        // ── Updaters ──────────────────────────────────────────────────────────────
        suspend fun setLanguage(code: String) = dataStore.edit { it[Keys.SELECTED_LANGUAGE] = code }

        suspend fun setOnboardingComplete(complete: Boolean) = dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }

        suspend fun setNotificationListenerGranted(granted: Boolean) = dataStore.edit { it[Keys.NOTIFICATION_LISTENER_GRANTED] = granted }

        /** Records consent for a specific feature. [version] is the consent policy version. */
        suspend fun recordConsent(
            sms: Boolean = false,
            camera: Boolean = false,
            location: Boolean = false,
            analytics: Boolean = false,
            version: Int = 1,
        ) = dataStore.edit { prefs ->
            if (sms) prefs[Keys.SMS_CONSENT_GRANTED] = true
            if (camera) prefs[Keys.CAMERA_CONSENT_GRANTED] = true
            if (location) prefs[Keys.LOCATION_CONSENT_GRANTED] = true
            if (analytics) prefs[Keys.ANALYTICS_CONSENT] = true
            prefs[Keys.CONSENT_VERSION] = version
            prefs[Keys.CONSENT_TIMESTAMP_MS] = System.currentTimeMillis()
        }

        suspend fun setUseOfflineFallback(use: Boolean) = dataStore.edit { it[Keys.USE_OFFLINE_FALLBACK] = use }
    }

/**
 * Immutable snapshot of user preferences for the UI layer.
 * Exposed via [UserPreferencesDataStore.userPreferences] Flow.
 */
data class UserPreferences(
    val selectedLanguage: String,
    val notificationsEnabled: Boolean,
    val onboardingComplete: Boolean,
    val notificationListenerGranted: Boolean,
    val smsConsentGranted: Boolean,
    val cameraConsentGranted: Boolean,
    val locationConsentGranted: Boolean,
    val analyticsConsent: Boolean,
    /** Version of the consent policy the user agreed to (DPDP audit trail). */
    val consentVersion: Int,
    /** Epoch millis when consent was last recorded (DPDP audit trail). */
    val consentTimestampMs: Long,
    val useOfflineFallback: Boolean,
    val fraudHeatmapLastSyncMs: Long,
)
