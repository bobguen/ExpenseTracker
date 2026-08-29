package com.expensetracker.ml

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TFLiteClassifier @Inject constructor(@ApplicationContext private val context: Context) {
    // Lightweight keyword fallback until quantized model is bundled (target <3MB)
    // Real TFLite interpreter will replace this via assets/expense_classifier.tflite
    private val keywords = mapOf(
        "grocery" to "Food", "restaurant" to "Food", "coffee" to "Food", "lunch" to "Food", "dinner" to "Food", "starbucks" to "Food",
        "uber" to "Transport", "taxi" to "Transport", "bus" to "Transport", "metro" to "Transport", "fuel" to "Transport", "parking" to "Transport",
        "shirt" to "Shopping", "shoes" to "Shopping", "amazon" to "Shopping", "mall" to "Shopping",
        "rent" to "Bills", "electric" to "Bills", "water" to "Bills", "internet" to "Bills", "phone" to "Bills",
        "doctor" to "Health", "pharmacy" to "Health", "hospital" to "Health",
        "movie" to "Entertainment", "cinema" to "Entertainment", "game" to "Entertainment", "netflix" to "Entertainment"
    )

    fun classify(text: String): String {
        val lower = text.lowercase()
        // Try TFLite if model exists
        try {
            context.assets.open("expense_classifier.tflite").use { /* if exists, run interpreter; fallback below if not */ }
            // TODO: Interpreter inference with vocab.txt, seq_len 32; omitted for initial build to keep APK <30MB
        } catch (e: Exception) { /* model not bundled yet, use keyword */ }
        for ((kw, cat) in keywords) if (lower.contains(kw)) return cat
        return "Other"
    }

    fun classifyWithConfidence(text: String): Pair<String, Float> {
        val cat = classify(text)
        val conf = if (cat == "Other") 0.45f else 0.85f
        return cat to conf
    }
}
