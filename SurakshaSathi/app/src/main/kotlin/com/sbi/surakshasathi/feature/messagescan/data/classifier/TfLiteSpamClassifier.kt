package com.sbi.surakshasathi.feature.messagescan.data.classifier

import android.content.Context
import android.content.res.AssetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device spam/phishing classifier using TensorFlow Lite.
 *
 * Model spec:
 * - File: assets/spam_classifier.tflite (INT8-quantized, §8B)
 * - Vocab: assets/vocab.txt (one token per line, index = line number)
 * - Input: [1, MAX_SEQ_LEN] int32 token IDs
 * - Output: [1, 1] float32 phishing probability (0.0 – 1.0)
 *
 * Loaded via [MappedByteBuffer] (no full-RAM copy, §8B).
 * Released when the calling scope ends (call [close] explicitly).
 *
 * Fallback: if model fails to load or throws during inference,
 * [fallbackKeywordScore] is returned instead — the app never crashes.
 *
 * Swapping real trained weights: drop the new spam_classifier.tflite
 * into assets/ — zero code change required (§1.4 constraint).
 */
@Singleton
class TfLiteSpamClassifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : OnDeviceClassifier {
        override val name: String = "TfLiteSpamClassifier"

        private val MAX_SEQ_LEN = 128
        private val UNKNOWN_TOKEN_ID = 1
        private val PAD_TOKEN_ID = 0

        private var interpreter: Interpreter? = null
        private var vocab: Map<String, Int> = emptyMap()

        init {
            loadModelAndVocab()
        }

        private fun loadModelAndVocab() {
            try {
                val modelBuffer: MappedByteBuffer = FileUtil.loadMappedFile(context, "spam_classifier.tflite")
                val options =
                    Interpreter.Options().apply {
                        numThreads = 2
                        useXNNPACK = true
                    }
                interpreter = Interpreter(modelBuffer, options)
                vocab = loadVocab(context.assets)
            } catch (e: Exception) {
                // Model load failed — fallback keyword heuristic will be used
                // Never log the stack trace in release builds
                interpreter = null
            }
        }

        private fun loadVocab(assets: AssetManager): Map<String, Int> {
            return try {
                BufferedReader(InputStreamReader(assets.open("vocab.txt"))).use { reader ->
                    reader.lineSequence()
                        .mapIndexed { index, token -> token.trim().lowercase() to index }
                        .toMap()
                }
            } catch (e: Exception) {
                emptyMap()
            }
        }

        override fun classify(text: String): Float {
            val interp = interpreter ?: return fallbackKeywordScore(text)

            return try {
                val tokenIds = tokenize(text)
                val input = Array(1) { IntArray(MAX_SEQ_LEN) }
                tokenIds.forEachIndexed { i, id -> if (i < MAX_SEQ_LEN) input[0][i] = id }

                val output = Array(1) { FloatArray(1) }
                interp.run(input, output)

                output[0][0].coerceIn(0f, 1f)
            } catch (e: Exception) {
                fallbackKeywordScore(text)
            }
        }

        /**
         * Tokenizes [text] into a list of vocab IDs.
         * Simple whitespace + punctuation splitting for the placeholder model.
         * The real model may ship with a SentencePiece or WordPiece tokenizer —
         * update this method when the real weights arrive.
         */
        private fun tokenize(text: String): List<Int> {
            val tokens =
                text.lowercase()
                    .replace(Regex("[^a-z0-9\\s]"), " ")
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }

            return tokens.map { token -> vocab[token] ?: UNKNOWN_TOKEN_ID }
        }

        /**
         * Keyword heuristic fallback — used when the TFLite model is unavailable.
         * Returns a risk score [0.0, 1.0] based on high-signal phishing keywords.
         * This is a SAFETY NET, not a replacement for the real model.
         */
        private fun fallbackKeywordScore(text: String): Float {
            val lower = text.lowercase()
            var score = 0f
            PHISHING_KEYWORDS.forEach { (keyword, weight) ->
                if (lower.contains(keyword)) score += weight
            }
            return score.coerceIn(0f, 1f)
        }

        /** Release TFLite interpreter memory. Call when the owning scope ends. */
        fun close() {
            interpreter?.close()
            interpreter = null
        }

        companion object {
            /** High-signal phishing keywords and their score contribution. */
            private val PHISHING_KEYWORDS =
                listOf(
                    "kyc" to 0.25f,
                    "update kyc" to 0.35f,
                    "account blocked" to 0.35f,
                    "account suspended" to 0.35f,
                    "click here" to 0.20f,
                    "verify now" to 0.25f,
                    "otp" to 0.15f,
                    "share otp" to 0.40f,
                    "mpin" to 0.20f,
                    "share mpin" to 0.45f,
                    "urgent" to 0.15f,
                    "immediate action" to 0.20f,
                    "win" to 0.10f,
                    "prize" to 0.10f,
                    "reward" to 0.10f,
                    "lottery" to 0.15f,
                    "sbi yono" to 0.10f, // Low alone — needs context
                    "fake yono" to 0.50f,
                    ".apk" to 0.30f,
                    "install" to 0.10f,
                    "download" to 0.10f,
                    "bit.ly" to 0.20f,
                    "tinyurl" to 0.20f,
                )
        }
    }
