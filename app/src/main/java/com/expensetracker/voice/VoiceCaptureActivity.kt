package com.expensetracker.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.expensetracker.data.local.TransactionEntity
import com.expensetracker.data.local.AppDatabase
import com.expensetracker.ml.TFLiteClassifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Locale

@AndroidEntryPoint
class VoiceCaptureActivity : ComponentActivity() {
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var classifier: TFLiteClassifier
    private var recognizer: SpeechRecognizer? = null
    private var ttsManager: TTSManager? = null
    private var isConfirming = false
    private var lastParsed: AmountParser.Parsed? = null
    private var lastCategory: String = "Other"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsManager = TTSManager(this)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        setContent {
            var transcript by remember { mutableStateOf("Tap mic and say: '100 rupees for groceries'") }
            var status by remember { mutableStateOf("LISTENING") }
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Voice Expense Entry", style = MaterialTheme.typography.headlineSmall)
                        Text(transcript)
                        Text("Status: $status", style = MaterialTheme.typography.labelMedium)
                        Button(onClick = { startListening { t, s -> transcript = t; status = s } }) { Text("Start Listening") }
                        Button(onClick = { finish() }) { Text("Close") }
                    }
                }
            }
        }
        // Auto start
        startListening { _, _ -> }
    }

    private fun startListening(onResult: (String, String) -> Unit = {_,_->}) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            // Fallback to recognizer intent
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            startActivityForResult(intent, 1001)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull() ?: return
                if (!isConfirming) handleTranscribed(text, onResult) else handleConfirm(text, onResult)
            }
            override fun onError(error: Int) { onResult("Error $error, try again", "ERROR") }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }

    private fun handleTranscribed(text: String, onResult: (String, String) -> Unit) {
        val parsed = AmountParser.parse(text)
        if (parsed == null) {
            onResult("Didn't catch amount: $text", "RETRY")
            lifecycleScope.launch { ttsManager?.speak("I didn't catch the amount, please say again") }
            return
        }
        lastParsed = parsed
        lastCategory = classifier.classify(text)
        val confirmText = "I heard ${parsed.amountMinor/100.0} ${parsed.currency} for ${parsed.description} in $lastCategory, is that right?"
        onResult("$text\n -> $confirmText", "CONFIRMING")
        isConfirming = true
        lifecycleScope.launch {
            ttsManager?.speak(confirmText)
            // After TTS, listen for yes/no
            startListening(onResult)
        }
    }

    private fun handleConfirm(text: String, onResult: (String, String) -> Unit) {
        val lower = text.lowercase()
        when {
            lower.contains("yes") || lower.contains("yeah") || lower.contains("correct") || lower.contains("confirm") -> {
                lifecycleScope.launch {
                    lastParsed?.let { p ->
                        db.transactionDao().insert(
                            TransactionEntity(
                                amountMinor = p.amountMinor,
                                currencyCode = p.currency,
                                amountBaseMinor = p.amountMinor, // TODO convert via rate
                                baseCurrency = p.currency,
                                rateUsed = null,
                                rawText = text,
                                normalizedText = p.description,
                                category = lastCategory,
                                confidence = 0.9f,
                                timestampMs = System.currentTimeMillis(),
                                createdAtMs = System.currentTimeMillis()
                            )
                        )
                    }
                    ttsManager?.speak("Saved")
                    onResult("Saved: $lastParsed as $lastCategory", "SAVED")
                    isConfirming = false
                }
            }
            lower.contains("no") || lower.contains("nope") || lower.contains("wrong") -> {
                isConfirming = false
                onResult("Let's try again. What did you spend?", "RETRY")
                lifecycleScope.launch { ttsManager?.speak("Please say again") }
                startListening(onResult)
            }
            else -> {
                onResult("Please say yes or no", "AWAIT_YES_NO")
                startListening(onResult)
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        ttsManager?.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
            handleTranscribed(text) { _, _ -> }
        }
    }
}
