package com.alperen.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    companion object {
        private const val RECORD_AUDIO_REQUEST_CODE = 10
    }

    private lateinit var status: TextView
    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.BLACK)
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 34f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "JARVIS hazır."
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }
        val button = Button(this).apply {
            text = "🎙 DİNLE"
            textSize = 18f
            setOnClickListener { startListening() }
        }

        layout.addView(title, LinearLayout.LayoutParams(-1, -2))
        layout.addView(status, LinearLayout.LayoutParams(-1, -2))
        layout.addView(button, LinearLayout.LayoutParams(-1, -2))
        setContentView(layout)
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Bu cihazda konuşma tanıma kullanılamıyor."
            speak("Bu cihazda konuşma tanıma kullanılamıyor.")
            return
        }

        status.text = "Sizi dinliyorum..."
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    status.text = if (text.isBlank()) "Bir şey duyamadım." else "Siz: $text"
                    handleCommand(text.lowercase(Locale.getDefault()))
                    destroy()
                }
                override fun onError(error: Int) {
                    status.text = "Tekrar deneyin."
                    speak("Tekrar deneyin.")
                    destroy()
                }
                override fun onReadyForSpeech(p0: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(p0: Float) {}
                override fun onBufferReceived(p0: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p0: Bundle?) {}
                override fun onEvent(p0: Int, p1: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS sizi dinliyor")
        }
        recognizer?.startListening(intent)
    }

    private fun handleCommand(command: String) {
        when {
            command.contains("saat") -> {
                val time = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date())
                status.text = "Saat: $time"
                speak("Saat $time")
            }
            command.contains("youtube") -> {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com")))
                speak("YouTube'u açıyorum.")
            }
            command.contains("hava") -> {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=hava+durumu")))
                speak("Hava durumunu arıyorum.")
            }
            command.contains("ses aç") || command.contains("sesi aç") -> {
                val audio = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                audio.adjustVolume(android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                speak("Sesi açıyorum.")
            }
            command.contains("merhaba") || command.contains("selam") -> {
                speak("Merhaba. Size nasıl yardımcı olabilirim?")
            }
            else -> speak("Komutu anladım: $command")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                status.text = "Mikrofon izni verilmedi."
                speak("Mikrofon izni olmadan sizi duyamam.")
            }
        }
    }

    private fun speak(text: String) {
        if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("tr", "TR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                status.text = "Cihazda Türkçe sesli okuma paketi bulunamadı."
            }
        } else {
            status.text = "Sesli okuma başlatılamadı."
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
