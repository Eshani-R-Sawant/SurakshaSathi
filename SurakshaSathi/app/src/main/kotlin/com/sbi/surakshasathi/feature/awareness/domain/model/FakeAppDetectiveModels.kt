package com.sbi.surakshasathi.feature.awareness.domain.model

/** One app/offer tile the player can tap — Compose-drawn monogram, never a real bitmap logo. */
data class AppTile(
    val id: String,
    val label: String,
    val developer: String,
    val downloads: String,
    val rating: String,
    val isGenuine: Boolean,
)

/** One "spot the genuine app" round for Fake App Detective. */
data class FakeAppRound(
    val id: String,
    val prompt: String,
    val tiles: List<AppTile>,
    val personaTags: List<String>,
    val explanation: String,
)
