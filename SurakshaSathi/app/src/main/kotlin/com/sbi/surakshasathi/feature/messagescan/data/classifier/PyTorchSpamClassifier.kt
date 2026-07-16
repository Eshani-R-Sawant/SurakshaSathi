package com.sbi.surakshasathi.feature.messagescan.data.classifier

import android.content.Context
import com.sbi.surakshasathi.BuildConfig
import com.sbi.surakshasathi.feature.messagescan.data.classifier.tokenizer.AlbertUnigramTokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device spam/phishing classifier — the real trained `sms_spam_detector_v2` model
 * (ai4bharat/indic-bert backbone + 39-dim auxiliary-feature fusion head), served via
 * PyTorch Mobile rather than TensorFlow Lite.
 *
 * Why PyTorch Mobile instead of TFLite (§1.4 originally called for a `.tflite` file): the
 * project's PyTorch->TFLite export pipeline (`sms_spam_detector_v2/src/quantize.py`, using
 * litert-torch) produces a model that cannot tell an obvious phishing message from a benign one —
 * verified directly against the checked-in `.tflite` files. The trained weights themselves are
 * correct (verified by running them un-exported: 93% on an obvious phishing message vs 5% on a
 * benign one); the export step is what's broken. That toolchain also has no Windows build at all,
 * so it can't be fixed/re-run in this environment. PyTorch's own official mobile export
 * (`torch.jit.trace` + `optimize_for_mobile` + `_save_for_lite_interpreter`, see
 * `sms_spam_detector_v2/scripts/export_pytorch_mobile.py`) traces the real, unmodified forward
 * pass and reproduces the correct behavior.
 *
 * Model spec:
 * - File: assets/spam_classifier_mobile.ptl (PyTorch Lite Interpreter format, FP32)
 * - Tokenizer: assets/spam_classifier_vocab.txt + spam_classifier_special_tokens.json, real
 *   SentencePiece Unigram vocab exported from the trained checkpoint (see [AlbertUnigramTokenizer])
 * - Inputs: input_ids [1,64] int64, attention_mask [1,64] int64, auxiliary_features [1,39] float32
 * - Output: [1,2] float32 — already softmax-ed (index 1 = spam probability) by the export wrapper
 *
 * NOT quantized: INT8 dynamic quantization (`torch.quantization.quantize_dynamic`) was tested and
 * found to break this model's discrimination entirely (collapses to ~0.52 regardless of input,
 * the same failure mode as the broken TFLite export) — see export_pytorch_mobile.py's docstring.
 * It also would only have saved ~10% of the file size, since the 200k-word multilingual
 * embedding table (not touched by dynamic quantization) dominates the model's footprint. Shipping
 * a correct-but-large model was judged better than a small-but-non-functional one.
 *
 * `auxiliary_features` is sent as all-zeros: porting `feature_extractor.py`'s 39-dim heuristic
 * vector to Kotlin (880 lines of per-language regex) was judged too large/risky to reproduce
 * faithfully in this pass. This is a smaller gap than it sounds — the equivalent rule signal
 * (URLs, APK links, urgency/OTP/KYC keywords, sender impersonation) is already computed on-device,
 * independently and in parallel, by [RuleBasedClassifier], which contributes 60% of
 * [HybridDecisionEngine]'s final score. The zero-filled aux vector mainly costs the *neural*
 * branch's own calibration headroom, not overall on-device coverage of those signals.
 *
 * Fallback: if the model or tokenizer fails to load, or inference throws, [fallbackKeywordScore]
 * is returned instead — the app never crashes.
 */
@Singleton
class PyTorchSpamClassifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : OnDeviceClassifier {
        override val name: String = "PyTorchSpamClassifier"

        /**
         * Lazy, not loaded in [init] — this is ~130MB of asset I/O plus native PyTorch module
         * init, which must never run on whatever thread happens to construct this Hilt singleton
         * (previously ran eagerly in `init {}`, which could land on the main thread and either
         * block app startup or, if the native load throws, crash it). First real [classify] call
         * pays this cost once; every call after reuses the cached result.
         *
         * `catch (t: Throwable)`, not `Exception` — PyTorch Mobile's native module load surfaces
         * failures (missing/incompatible native library, OOM on a large model) as [Error]
         * subtypes (e.g. `UnsatisfiedLinkError`, `OutOfMemoryError`), which a narrower
         * `catch (e: Exception)` does NOT catch and which previously crashed the app instead of
         * falling back to the keyword heuristic like a missing model file does.
         */
        private val loaded: LoadedModel? by lazy {
            try {
                val modelFile = copyAssetToFilesDirIfNeeded(MODEL_ASSET)
                LoadedModel(
                    module = LiteModuleLoader.load(modelFile.absolutePath),
                    tokenizer = AlbertUnigramTokenizer.loadFromAssets(context),
                )
            } catch (t: Throwable) {
                // Model/tokenizer load failed — fallback keyword heuristic will be used.
                // Never log the stack trace in release builds.
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("PyTorchSpamClassifier", "Model/tokenizer load failed", t)
                }
                null
            }
        }

        private data class LoadedModel(val module: Module, val tokenizer: AlbertUnigramTokenizer)

        /** PyTorch Mobile's [LiteModuleLoader] needs a real filesystem path, not an asset stream —
         * copies once into internal storage and reuses it on subsequent launches. */
        private fun copyAssetToFilesDirIfNeeded(assetName: String): File {
            val outFile = File(context.filesDir, assetName)
            val expectedSize = context.assets.openFd(assetName).use { it.length }
            if (outFile.exists() && outFile.length() == expectedSize) return outFile

            context.assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            return outFile
        }

        override fun classify(text: String): Float {
            val (mod, tok) = loaded ?: return fallbackKeywordScore(text)

            return try {
                val encoded = tok.encode(text, MAX_SEQ_LEN)
                val inputIds = LongArray(MAX_SEQ_LEN) { encoded.inputIds[it].toLong() }
                val attentionMask = LongArray(MAX_SEQ_LEN) { encoded.attentionMask[it].toLong() }
                val auxFeatures = FloatArray(AUX_DIM) // see class doc: zero-filled by design

                val inputIdsTensor = Tensor.fromBlob(inputIds, longArrayOf(1, MAX_SEQ_LEN.toLong()))
                val attentionMaskTensor = Tensor.fromBlob(attentionMask, longArrayOf(1, MAX_SEQ_LEN.toLong()))
                val auxTensor = Tensor.fromBlob(auxFeatures, longArrayOf(1, AUX_DIM.toLong()))

                val output =
                    mod.forward(
                        IValue.from(inputIdsTensor),
                        IValue.from(attentionMaskTensor),
                        IValue.from(auxTensor),
                    )
                val probs = output.toTensor().dataAsFloatArray
                probs[SPAM_CLASS_INDEX].coerceIn(0f, 1f)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("PyTorchSpamClassifier", "Inference failed", e)
                }
                fallbackKeywordScore(text)
            }
        }

        /**
         * Keyword heuristic fallback — used when the real model is unavailable.
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

        /** Release the PyTorch Mobile module. Call when the owning scope ends. */
        fun close() {
            loaded?.module?.destroy()
        }

        companion object {
            private const val MODEL_ASSET = "spam_classifier_mobile.ptl"
            private const val MAX_SEQ_LEN = 64
            private const val AUX_DIM = 39
            private const val SPAM_CLASS_INDEX = 1

            /** Real trained model's calibrated decision threshold (final_model_20260706_123010,
             * see optimal_threshold.json — tuned for 99% recall). [HybridDecisionEngine] uses its
             * own combined-score thresholds, not this one directly, but it's the reference point
             * this classifier's probabilities are calibrated against. */
            const val OPTIMAL_THRESHOLD = 0.5861971378326416f

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
                    "yono bank" to 0.10f, // Low alone — needs context
                    "fake yono" to 0.50f,
                    ".apk" to 0.30f,
                    "install" to 0.10f,
                    "download" to 0.10f,
                    "bit.ly" to 0.20f,
                    "tinyurl" to 0.20f,
                    // Non-banking financial scams (stock-tip / pump-and-dump) -- the original list
                    // was entirely bank-phishing-shaped (OTP/KYC/MPIN) and had zero signal for this
                    // fraud pattern, so a message like "Grindwell pre-open accumulation... Reply
                    // TOOL to view [link]" scored 0 and was never flagged.
                    "pre-open" to 0.20f,
                    "accumulation" to 0.20f,
                    "strategy ready" to 0.25f,
                    "reply tool" to 0.30f,
                    "target price" to 0.20f,
                    "intraday tip" to 0.25f,
                    "sure shot" to 0.30f,
                    "guaranteed return" to 0.35f,
                    "multibagger" to 0.25f,
                    "book profit" to 0.20f,
                )
        }
    }
