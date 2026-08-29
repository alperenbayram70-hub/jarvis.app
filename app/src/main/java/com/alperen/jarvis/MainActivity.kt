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

    private val prefs by lazy { getSharedPreferences("jarvis_memory", MODE_PRIVATE) }

    private fun getMemory(): String = prefs.getString("memory", "") ?: ""

    private fun addMemory(fact: String) {
        val current = getMemory()
        val updated = if (current.isBlank()) fact else "$current\n$fact"
        prefs.edit().putString("memory", updated).apply()
    }

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
            text = "DINLE"
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
        status.text = "DINLIYORUM..."
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (text.isBlank()) {
                        status.text = "BIR SEY DUYAMADIM"
                        reactor.state = ReactorState.IDLE
                    } else {
                        status.text = "SIZ: " + text
                        reactor.state = ReactorState.THINKING
                        handleCommand(text.lowercase(Locale.getDefault()))
                    }
                    destroy()
                }
                override fun onError(error: Int) {
                    status.text = "TEKRAR DENEYIN"
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
        if (command.contains("saat")) {
            val time = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date())
            status.text = "Saat: " + time
            speak("Saat " + time)
        } else if (command.contains("youtube")) {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com")))
            speak("YouTube'u aciyorum.")
        } else if (command.contains("hava")) {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=hava+durumu")))
            speak("Hava durumunu ariyorum.")
        } else if (command.contains("ses ac") || command.contains("sesi ac")) {
            val audio = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            audio.adjustVolume(android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
            speak("Sesi aciyorum.")
        } else if (command.contains("merhaba") || command.contains("selam")) {
            speak("Merhaba. Size nasil yardimci olabilirim?")
        } else if (command.startsWith("hatirla")) {
            val fact = command.removePrefix("hatirla").trim().removePrefix(":").trim()
            if (fact.isNotBlank()) {
                addMemory(fact)
                status.text = "NOT EDILDI"
                speak("Bunu hatirlayacagim.")
            } else {
                speak("Ne hatirlamami istediginizi anlamadim.")
            }
        } else if (command.contains("ne hatirliyorsun") || command.contains("neler biliyorsun")) {
            val mem = getMemory()
            if (mem.isBlank()) {
                speak("Henuz hicbir sey hatirlamiyorum.")
            } else {
                speak("Sunlari hatirliyorum: " + mem)
            }
        } else if (command.contains("hafizani sil") || command.contains("unut")) {
            prefs.edit().remove("memory").apply()
            status.text = "HAFIZA TEMIZLENDI"
            speak("Hafizami temizledim.")
        } else {
            askGemini(command)
        }
    }

    private fun askGemini(prompt: String) {
        status.text = "DUSUNUYORUM..."
        reactor.state = ReactorState.THINKING
        Thread {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=" + apiKey)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val memory = getMemory()
                val memoryPart = if (memory.isNotBlank()) memory else "henuz yok"
                val personaBuilder = StringBuilder()
                personaBuilder.append("Sen JARVIS'sin, sicak, samimi, esprili ama saygili bir kisisel asistansin. ")
                personaBuilder.append("Robotik veya resmi konusma, gercek bir arkadasla sohbet eder gibi konus. ")
                personaBuilder.append("Kisa ve dogal cumleler kur, gereksiz uzatma, sesli okunacagi icin akici ve konusma diline uygun yaz. ")
                personaBuilder.append("Turkce konus. ")
                personaBuilder.append("Kullanici hakkinda hatirladigin bilgiler: ")
                personaBuilder.append(memoryPart)
                personaBuilder.append(". Uygun oldugunda bu bilgileri dogal sekilde cevaplarina yedir, ama her seferinde tekrar etme.")
                val persona = personaBuilder.toString()

                val body = JSONObject()
                val systemInstruction = JSONObject()
                val systemParts = JSONArray()
                systemParts.put(JSONObject().put("text", persona))
                systemInstruction.put("parts", systemParts)
                body.put("system_instruction", systemInstruction)

                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray()
                parts.put(JSONObject().put("text", prompt))
                contentObj.put("parts", parts)
                contents.put(contentObj)
                body.put("contents", contents)

                val genConfig = JSONObject()
                genConfig.put("maxOutputTokens", 150)
                genConfig.put("temperature", 0.85)
                body.put("generationConfig", genConfig)

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
                    "HATA KODU " + responseCode + ": " + responseText
                }

                runOnUiThread {
                    status.text = answer
                    speak(answer)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Baglanti hatasi olustu."
                    speak("Baglanti hatasi olustu.")
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
                status.text = "MIKROFON IZNI VERILMEDI"
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
                status.text = "TURKCE SES PAKETI BULUNAMADI"
            }

            val maleVoice = tts.voices?.firstOrNull {
                it.locale.language == "tr" &&
                (it.name.contains("male", ignoreCase = true) || it.name.contains("erkek", ignoreCase = true))
            }
            if (maleVoice != null) {
                tts.voice = maleVoice
            }
            tts.setPitch(0.82f)
            tts.setSpeechRate(0.95f)

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
            status.text = "SESLI OKUMA BASLATILAMADI"
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
