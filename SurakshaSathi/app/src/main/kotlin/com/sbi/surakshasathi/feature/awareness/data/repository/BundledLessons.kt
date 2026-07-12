package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.feature.awareness.domain.model.Lesson
import com.sbi.surakshasathi.feature.awareness.domain.model.QuizQuestion

/**
 * Bundled offline default lesson set (§7c 5.2) — ships in the client so the
 * Learn tab is fully functional with no backend. Real content, not filler:
 * covers the three lessons the spec names explicitly.
 */
object BundledLessons {
    val ENGLISH: List<Lesson> =
        listOf(
            Lesson(
                id = "real_vs_fake_sbi",
                title = "Real vs Fake SBI Apps",
                description = "Learn the signs that separate the official YONO app from a fake one.",
                language = "en",
                badgeIdOnCompletion = "badge_yono_expert",
                quiz =
                    listOf(
                        QuizQuestion(
                            "A message asks you to install YONO from a link in WhatsApp. What should you do?",
                            listOf(
                                "Tap the link and install it",
                                "Only install YONO from the Play Store or sbi.co.in",
                                "Install it if the icon looks right",
                                "Forward it to a friend to check",
                            ),
                            correctOptionIndex = 1,
                        ),
                        QuizQuestion(
                            "Which of these is the strongest sign an app is fake, even if the icon and name look right?",
                            listOf(
                                "It asks for your name",
                                "Its signing certificate doesn't match SBI's official one",
                                "It has a splash screen",
                                "It's in English",
                            ),
                            correctOptionIndex = 1,
                        ),
                        QuizQuestion(
                            "The real YONO app will NEVER ask you to do which of these?",
                            listOf(
                                "Log in with your username",
                                "Share your OTP or MPIN over chat to \"verify\" your account",
                                "Show your balance",
                                "Let you set a PIN",
                            ),
                            correctOptionIndex = 1,
                        ),
                    ),
            ),
            Lesson(
                id = "never_share_otp",
                title = "Never Share Your OTP",
                description = "OTP-sharing is the #1 way fraudsters drain bank accounts. Learn why it's never okay.",
                language = "en",
                badgeIdOnCompletion = "badge_otp_guardian",
                quiz =
                    listOf(
                        QuizQuestion(
                            "Someone claiming to be an SBI officer calls and asks for your OTP to \"reverse a wrong transaction.\" What do you do?",
                            listOf(
                                "Share it, they sound official",
                                "Hang up — SBI staff never ask for your OTP",
                                "Share only the first 3 digits",
                                "Ask them to call back later",
                            ),
                            correctOptionIndex = 1,
                        ),
                        QuizQuestion(
                            "An OTP is meant to prove:",
                            listOf(
                                "Who you're talking to on the phone",
                                "That the person completing the transaction is you",
                                "Your account balance",
                                "Your KYC status",
                            ),
                            correctOptionIndex = 1,
                        ),
                        QuizQuestion(
                            "If you accidentally shared your OTP with a scammer, what's the FIRST thing to do?",
                            listOf(
                                "Wait and see if money is deducted",
                                "Immediately call SBI's helpline / block your card via the app",
                                "Change your phone's lock screen PIN",
                                "Delete the SMS",
                            ),
                            correctOptionIndex = 1,
                        ),
                    ),
            ),
            Lesson(
                id = "safe_yono_download",
                title = "Safe Ways to Download YONO",
                description = "The only three places you should ever get YONO from.",
                language = "en",
                badgeIdOnCompletion = "badge_safe_downloader",
                quiz =
                    listOf(
                        QuizQuestion(
                            "Which of these is a SAFE way to get YONO?",
                            listOf(
                                "A .apk file shared on WhatsApp",
                                "The Google Play Store",
                                "A link in an SMS offering a reward",
                                "A QR code from an unknown flyer",
                            ),
                            correctOptionIndex = 1,
                        ),
                        QuizQuestion(
                            "You want to be extra sure a QR code leads to the real SBI site. What should SurakshaSathi's scanner check?",
                            listOf(
                                "Only the QR code's color",
                                "The domain/package against SBI's official allow-list and signing certificate",
                                "How many times the code has been scanned",
                                "The QR code's size",
                            ),
                            correctOptionIndex = 1,
                        ),
                        QuizQuestion(
                            "If a downloaded file ends in .apk and didn't come from the Play Store, SurakshaSathi will:",
                            listOf(
                                "Ignore it",
                                "Scan it automatically before you install it",
                                "Only scan it if you ask",
                                "Delete it without asking",
                            ),
                            correctOptionIndex = 1,
                        ),
                    ),
            ),
        )
}
