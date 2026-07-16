package com.sbi.surakshasathi.app.navigation

/**
 * All navigation routes in the SurakshaSathi app.
 *
 * Uses a sealed class hierarchy so routes are exhaustive and typo-safe.
 * Deep link routes (e.g. from notification "Report" action) must match
 * the intent-filter in AndroidManifest.xml.
 */
sealed class Screen(val route: String) {
    // ── Onboarding ─────────────────────────────────────────────────────────────
    data object Splash : Screen("splash")

    data object Onboarding : Screen("onboarding")

    data object Permissions : Screen("permissions")

    // ── Main Bottom-Nav Tabs ───────────────────────────────────────────────────
    data object Home : Screen("home")

    data object Alerts : Screen("alerts")

    data object Dashboard : Screen("dashboard")

    data object Learn : Screen("learn")

    // ── Feature Screens ────────────────────────────────────────────────────────

    // Flow 1: Message Scan
    data object MessageDetail : Screen("message/{messageId}") {
        fun createRoute(id: Long) = "message/$id"
    }

    // Flow 1b: RAG Warning
    data object RagWarning : Screen("rag_warning/{messageId}") {
        fun createRoute(id: Long) = "rag_warning/$id"
    }

    // Flow 2: APK Scan
    data object ApkScan : Screen("apk_scan")

    data object ApkAlert : Screen("apk_alert/{packageName}") {
        fun createRoute(pkg: String) = "apk_alert/$pkg"
    }

    // Flow 3: Adaptive Friction
    data object ConfirmTransfer : Screen("confirm_transfer")

    data object LivenessCheck : Screen("liveness_check")

    // Flow 4a: Fraud Dashboard
    data object FraudHeatmap : Screen("fraud_heatmap")

    // Flow 4b: NCRP Report. contextType selects which auto-populate path NcrpReportViewModel
    // uses ("message" | "apk" | "manual") -- previously a single ambiguous contextId string was
    // sniffed (numeric = message, else = APK package name), which meant a manual report
    // (no message/APK context) silently misfired into the APK lookup branch.
    data object NcrpReport : Screen("ncrp_report/{contextType}/{contextId}") {
        fun createRouteForMessage(messageId: Long) = "ncrp_report/message/$messageId"

        fun createRouteForApk(packageName: String) = "ncrp_report/apk/$packageName"

        fun createRouteManual() = "ncrp_report/manual/none"
    }

    // Flow 5: Awareness
    data object OfficialLinkScanner : Screen("official_link_scanner")

    data object LessonList : Screen("lesson_list")

    data object LessonDetail : Screen("lesson/{lessonId}") {
        fun createRoute(id: String) = "lesson/$id"
    }

    data object Badges : Screen("badges")

    // Flow 7: Games & Advisories
    data object GameHub : Screen("game_hub")

    data object ShieldDefenderGame : Screen("game/shield_defender")

    data object FakeAppDetectiveGame : Screen("game/fake_app_detective")

    data object FraudTrafficControlGame : Screen("game/fraud_traffic_control")

    data object BubblePopScamGame : Screen("game/bubble_pop_scam")

    data object SecurePhoneBuilderGame : Screen("game/secure_phone_builder")

    data object AdvisoryList : Screen("advisories")

    data object AdvisoryDetail : Screen("advisory/{advisoryId}") {
        fun createRoute(id: String) = "advisory/$id"
    }
}
