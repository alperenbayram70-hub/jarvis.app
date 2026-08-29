package com.alperen.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    companion object {
        private const val RECORD_AUDIO_REQUEST_CODE = 10
    }

    private lateinit var status: TextView
    private lateinit var reactor: ReactorView
    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val title = TextView(this).apply {
            text = "J . A . R . V . I . S"
            textSize = 24f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
        }

        reactor = ReactorView(this)
        val reactorParams = LinearLayout.LayoutParams(560, 560).apply {
            topMargin = 50
            bottomMargin = 50
        }

        status = TextView(this).apply {
            text = "SİSTEM HAZIR"
            textSize = 15f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 50)
        }

        val button = Button(this).apply {
            text = "◉  DİNLE"
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#00E5FF"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 60f
                setStroke(3, Color.parseColor("#00E5FF"))
                setColor(Color.parseColor("#1A000000"))
            }
            setPadding(60, 28, 60, 28)
            setOnClickListener { startListening() }
        }

        layout.addView(title, LinearLayout.LayoutParams(-2, -2))
        layout.addView(reactor, reactorParams)
        layout.addView(status, LinearLayout.LayoutParams(-1, -2))
        layout.addView(button, LinearLayout.LayoutParams(-2, -2))
        setContentView(layout)
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "SES TANIMA KULLANILAMIYOR"
            speak("Bu cihazda konuşma tanıma kullanılamıyor.")
            return
        }

        reactor.state = ReactorState.LISTENING
        status.text = "DİNLİYORUM..."
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isBlank()) {
                        status.text = "BİR ŞEY DUYAMADIM"
                        reactor.state = ReactorState.IDLE
                    } else {
                        status.text = "SİZ: $text"
                        reactor.state = ReactorState.THINKING
                        handleCommand(text.lowercase(Locale.getDefault()))
                    }
                    destroy()
                }
                override fun onError(error: Int) {
                    status.text = "TEKRAR DENEYİN"
                    reactor.state = ReactorState.IDLE
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
            else -> askGemini(command)
        }
    }

    private fun askGemini(prompt: String) {
        status.text = "DÜŞÜNÜYORUM..."
        reactor.state = ReactorState.THINKING
        Thread {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$apiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val body = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().apply {
                            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                        }
                    ))
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 150)
                        put("temperature", 0.7)
                    })
                }

                connection.outputStream.use { it.write(body.toString().toByteArray()) }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream.bufferedReader().use { it.readText() }

                val answer = try {
                    JSONObject(responseText)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                } catch (e: Exception) {
                    "Yapay zekadan cevap alınamadı."
                }

                runOnUiThread {
                    status.text = answer
                    speak(answer)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Bağlantı hatası oluştu."
                    speak("Bağlantı hatası oluştu.")
                }
            }
        }.start()
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
                status.text = "MİKROFON İZNİ VERİLMEDİ"
                reactor.state = ReactorState.IDLE
                speak("Mikrofon izni olmadan sizi duyamam.")
            }
        }
    }

    private fun speak(text: String) {
        reactor.state = ReactorState.SPEAKING
        if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("tr", "TR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                status.text = "TÜRKÇE SES PAKETİ BULUNAMADI"
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { reactor.state = ReactorState.IDLE }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { reactor.state = ReactorState.IDLE }
                }
            })
        } else {
            status.text = "SESLİ OKUMA BAŞLATILAMADI"
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
