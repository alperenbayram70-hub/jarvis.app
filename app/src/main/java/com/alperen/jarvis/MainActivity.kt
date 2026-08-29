package com.alperen.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.SmsManager
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
        private const val PERMISSION_REQUEST_CODE = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
    }

    private lateinit var status: TextView
    private lateinit var reactor: ReactorView
    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech

    private val prefs by lazy { getSharedPreferences("jarvis_memory", MODE_PRIVATE) }

    private fun getMemory(): String = prefs.getString("memory", "") ?: ""

    private fun addMemory(fact: String) {
        val current = getMemory()
        val updated = if (current.isBlank()) fact else current + "\n" + fact
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
            text = "SISTEM HAZIR"
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

        requestMissingPermissions()
    }

    private fun requestMissingPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMissingPermissions()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "SES TANIMA KULLANILAMIYOR"
            speak("Bu cihazda konusma tanima kullanilamiyor.")
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
                        handleCommand(text.lowercase(Locale.getDefault()), text)
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

    // Rehberden isim gecerek numara bulur
    private fun findContactNumber(name: String): String? {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )
        var result: String? = null
        cursor?.use {
            while (it.moveToNext()) {
                val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                if (displayName != null && displayName.lowercase(Locale.getDefault()).contains(name)) {
                    result = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    return@use
                }
            }
        }
        return result
    }

    // Yuklu uygulamalar arasinda isimle eslesen paketi bulur
    private fun findAppPackage(appName: String): String? {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (appInfo in apps) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.lowercase(Locale.getDefault()).contains(appName)) {
                return appInfo.packageName
            }
        }
        return null
    }

    private fun handleCommand(command: String, originalText: String) {
        if (command.contains("saat")) {
            val time = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date())
            status.text = "Saat: " + time
            speak("Saat " + time)

        } else if (command.contains("youtube")) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
            speak("YouTube'u aciyorum.")

        } else if (command.contains("hava")) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=hava+durumu")))
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

        } else if (command.startsWith("ara ") || command.contains("'i ara") || command.contains("'yi ara") || command.contains("i ara") || command.contains("yi ara")) {
            // Ornek: "ahmet'i ara" veya "ara ahmet"
            val name = command
                .replace("ara", "")
                .replace("'i", "")
                .replace("'yi", "")
                .trim()
            if (name.isBlank()) {
                speak("Kimi aramami istediginizi anlamadim.")
            } else {
                val number = findContactNumber(name)
                if (number != null) {
                    if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number))
                        startActivity(callIntent)
                        speak(name + " araniyor.")
                    } else {
                        speak("Arama izni verilmemis.")
                    }
                } else {
                    speak(name + " rehberde bulunamadi.")
                }
            }

        } else if (command.contains("mesaj gonder") || command.contains("mesaj at")) {
            // Ornek: "ahmet'e mesaj gonder: bugun saat 5te bulusalim"
            val parts = originalText.split(":", limit = 2)
            if (parts.size < 2) {
                speak("Mesaji nasil yazmami istediginizi anlamadim. Ornek: ahmete mesaj gonder iki nokta ust uste mesajiniz.")
            } else {
                val recipientPart = parts[0].lowercase(Locale.getDefault())
                val messageBody = parts[1].trim()
                val name = recipientPart
                    .replace("mesaj gonder", "")
                    .replace("mesaj at", "")
                    .replace("'e", "")
                    .replace("'a", "")
                    .replace("e", "")
                    .trim()
                val number = findContactNumber(name)
                if (number != null) {
                    if (checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            val smsManager = SmsManager.getDefault()
                            smsManager.sendTextMessage(number, null, messageBody, null, null)
                            speak(name + " kisisine mesaj gonderildi.")
                        } catch (e: Exception) {
                            speak("Mesaj gonderilirken bir hata olustu.")
                        }
                    } else {
                        speak("Mesaj gonderme izni verilmemis.")
                    }
                } else {
                    speak(name + " rehberde bulunamadi.")
                }
            }

        } else if (command.contains(" ac") && !command.contains("sesi ac") && !command.contains("ses ac")) {
            // Ornek: "spotify ac" veya "whatsapp'i ac"
            val appName = command
                .replace(" ac", "")
                .replace("'i", "")
                .replace("'yi", "")
                .trim()
            val packageName = findAppPackage(appName)
            if (packageName != null) {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    speak(appName + " aciliyor.")
                } else {
                    speak(appName + " acilamadi.")
                }
            } else {
                speak(appName + " adinda bir uygulama bulamadim.")
            }

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
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash-lite:generateContent?key=" + apiKey)
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
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                status.text = "BAZI IZINLER VERILMEDI"
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
