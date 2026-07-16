package com.sbi.surakshasathi.feature.awareness.data.repository

import com.sbi.surakshasathi.feature.awareness.domain.model.AppTile
import com.sbi.surakshasathi.feature.awareness.domain.model.FakeAppRound
import com.sbi.surakshasathi.feature.awareness.domain.usecase.GENERAL_PERSONA

/** Bundled "spot the genuine app" round pool for Fake App Detective, tagged by persona. */
object BundledFakeAppRounds {
    val ENGLISH: List<FakeAppRound> =
        listOf(
            FakeAppRound(
                id = "yono_lookalikes",
                prompt = "Which of these is the real YONO Bank app?",
                tiles =
                    listOf(
                        AppTile("yono_real", "YONO Bank", "The Bank", "10 Cr+ downloads", "4.3★", isGenuine = true),
                        AppTile("yono_pro", "YONO Pro Plus", "Bank Digital Services Pvt", "500+ downloads", "2.1★", isGenuine = false),
                        AppTile("yono_rewards", "Bank Rewards Cash", "Reward Apps Inc", "100+ downloads", "1.8★", isGenuine = false),
                    ),
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "The real YONO Bank app is published by \"The Bank\" with crore-plus downloads and a solid rating. Copycats use similar names but an unrelated developer and almost no reviews.",
            ),
            FakeAppRound(
                id = "internship_apps",
                prompt = "Which is the real internship application you applied through your college for?",
                tiles =
                    listOf(
                        AppTile("intern_real", "CareerConnect Campus", "Official College Placement Cell", "50K+ downloads", "4.1★", isGenuine = true),
                        AppTile("intern_elite", "InternshipPro Elite", "Elite Careers Global", "200+ downloads", "2.4★", isGenuine = false),
                        AppTile("intern_free", "Free Internship 2025", "QuickJobs Media", "80+ downloads", "1.5★", isGenuine = false),
                    ),
                personaTags = listOf("student"),
                explanation = "Genuine placement apps are published by your college or a well-known jobs platform with a track record. \"Free Internship\" or \"Elite\" branding with tiny download counts is a classic scholarship/internship-APK scam pattern.",
            ),
            FakeAppRound(
                id = "gst_filing_apps",
                prompt = "Which is the real GST filing portal app?",
                tiles =
                    listOf(
                        AppTile("gst_real", "GST Portal", "Goods and Services Tax Network", "10L+ downloads", "4.0★", isGenuine = true),
                        AppTile("gst_refund", "GST Refund Fast", "TaxHelp Solutions", "1K+ downloads", "2.2★", isGenuine = false),
                        AppTile("gst_easy", "GSTN Easy Filing", "Filing Experts Co", "600+ downloads", "1.9★", isGenuine = false),
                    ),
                personaTags = listOf("business_owner"),
                explanation = "GSTN is the only official filing body. Apps promising a faster \"refund\" from an unrelated private developer are built to harvest your GSTIN and bank details.",
            ),
            FakeAppRound(
                id = "pension_apps",
                prompt = "Which is the real app for submitting your life certificate?",
                tiles =
                    listOf(
                        AppTile("pension_real", "Jeevan Pramaan", "Ministry of Electronics & IT", "1 Cr+ downloads", "3.9★", isGenuine = true),
                        AppTile("pension_update", "Pension Update Now", "PensionCare Services", "300+ downloads", "2.0★", isGenuine = false),
                        AppTile("pension_epf", "EPF Pension Care", "Retiree Support Group", "150+ downloads", "1.7★", isGenuine = false),
                    ),
                personaTags = listOf("senior_citizen"),
                explanation = "Jeevan Pramaan is the only government digital life certificate app. \"Urgent update\" apps from unknown publishers are built to steal your pension account details.",
            ),
            FakeAppRound(
                id = "lpg_subsidy_apps",
                prompt = "Which is the real app for checking your LPG subsidy?",
                tiles =
                    listOf(
                        AppTile("lpg_real", "Indane Gas", "Indian Oil Corporation Ltd", "50L+ downloads", "3.8★", isGenuine = true),
                        AppTile("lpg_subsidy", "LPG Subsidy Refund", "Subsidy Direct Pvt", "800+ downloads", "2.1★", isGenuine = false),
                        AppTile("lpg_cashback", "Gas Cashback Offer", "SavingsHub", "200+ downloads", "1.6★", isGenuine = false),
                    ),
                personaTags = listOf("homemaker"),
                explanation = "LPG subsidy status only appears in your official gas provider's app (Indane/HP/Bharat Gas). \"Refund\" or \"cashback\" apps are built to ask for your bank PIN.",
            ),
            FakeAppRound(
                id = "tax_filing_apps",
                prompt = "Which is the real income tax e-filing app?",
                tiles =
                    listOf(
                        AppTile("tax_real", "Income Tax Department", "Income Tax Department, Govt of India", "1 Cr+ downloads", "4.2★", isGenuine = true),
                        AppTile("tax_refund", "IT Refund Express", "RefundPro Technologies", "2K+ downloads", "2.3★", isGenuine = false),
                        AppTile("tax_saver", "TaxSaver Pro", "SmartFinance Apps", "900+ downloads", "2.0★", isGenuine = false),
                    ),
                personaTags = listOf("salaried_professional"),
                explanation = "Only the official Income Tax Department app is linked to your PAN records. \"Refund Express\" apps are phishing for your PAN, Aadhaar, and bank login together.",
            ),
            FakeAppRound(
                id = "whatsapp_lookalikes",
                prompt = "Which is the real WhatsApp?",
                tiles =
                    listOf(
                        AppTile("wa_real", "WhatsApp Messenger", "WhatsApp LLC (Meta)", "500 Cr+ downloads", "4.0★", isGenuine = true),
                        AppTile("wa_plus", "WhatsApp Plus", "Unknown Developer", "1K+ downloads", "2.5★", isGenuine = false),
                        AppTile("wa_gb", "GB WhatsApp", "GBWhats Team", "5K+ downloads", "2.8★", isGenuine = false),
                    ),
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "Modded WhatsApp clones aren't on the Play Store, aren't published by Meta, and can read every message you send — they exist purely to harvest data.",
            ),
            FakeAppRound(
                id = "merchant_payment_apps",
                prompt = "Which is the real app for collecting UPI payments from customers?",
                tiles =
                    listOf(
                        AppTile("upi_real", "BHIM", "National Payments Corporation of India", "10 Cr+ downloads", "4.1★", isGenuine = true),
                        AppTile("upi_cashback", "UPI Cashback King", "CashbackHub", "3K+ downloads", "2.2★", isGenuine = false),
                        AppTile("upi_merchant", "Merchant Pay Fast", "QuickPay Solutions", "1K+ downloads", "1.9★", isGenuine = false),
                    ),
                personaTags = listOf("business_owner"),
                explanation = "BHIM is built by NPCI, the body that runs UPI itself. Third-party \"cashback\" collection apps often quietly change QR codes to redirect payments to the scammer.",
            ),
            FakeAppRound(
                id = "scholarship_portal_apps",
                prompt = "Which is the real national scholarship portal app?",
                tiles =
                    listOf(
                        AppTile("nsp_real", "National Scholarship Portal", "Ministry of Education, Govt of India", "10L+ downloads", "3.7★", isGenuine = true),
                        AppTile("nsp_apply", "Scholarship2025 Apply", "EduFund Direct", "700+ downloads", "2.0★", isGenuine = false),
                        AppTile("nsp_free", "Free Scholarship Now", "StudentAid Global", "400+ downloads", "1.6★", isGenuine = false),
                    ),
                personaTags = listOf("student"),
                explanation = "The National Scholarship Portal is the single official gateway for government scholarships. Copycat \"apply now\" apps ask for an upfront \"processing fee\" — a scholarship scam red flag.",
            ),
            FakeAppRound(
                id = "play_store_lookalikes",
                prompt = "Which is the real Google Play Store?",
                tiles =
                    listOf(
                        AppTile("play_real", "Play Store", "Google LLC", "1000 Cr+ downloads", "4.4★", isGenuine = true),
                        AppTile("play_update", "Play Store Update", "System Tools Inc", "2K+ downloads", "2.1★", isGenuine = false),
                        AppTile("play_market", "App Market Pro", "MarketPlace Apps", "900+ downloads", "1.8★", isGenuine = false),
                    ),
                personaTags = listOf(GENERAL_PERSONA),
                explanation = "The Play Store never needs to be \"updated\" by installing a separate app — that prompt itself is always fake and used to sideload malware.",
            ),
        )
}
