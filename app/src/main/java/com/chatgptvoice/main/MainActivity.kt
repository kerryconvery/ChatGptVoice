package com.chatgptvoice.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aallam.openai.api.completion.CompletionRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.chatgptvoice.main.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity(), RecognitionListener{
    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private lateinit var tts: TextToSpeech
    private lateinit var conversationView: TextView
    private lateinit var speakButton: Button
    private lateinit var conversationHistory: SpannableStringBuilder

    companion object {
        /**
         * Put any keyword that will trigger the speech recognition
         */
        private const val RECORD_AUDIO_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        conversationHistory = SpannableStringBuilder()
        conversationView = findViewById(R.id.conversationView)
        speakButton = findViewById(R.id.speakButton)

        speakButton.setOnClickListener {
            speechRecognizer.startListening(speechRecognizerIntent)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)

        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test")
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.US
            }
        }
    }

    override fun onResume() {
        super.onResume()

//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
//            speechRecognizer.startListening(speechRecognizerIntent)
//        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return super.onSupportNavigateUp()
    }

    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {}

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches != null) {
            processInput(matches[0])
        }
    }

    private fun processInput(text: String) {
        printInput(text)
        callChatGPTAPI(text)
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun callChatGPTAPI(input: String) {

        val client = OkHttpClient()
        val requestParameters = JSONObject()

        requestParameters.put("model", "text-davinci-003")
        requestParameters.put("temperature", "0.5")
        requestParameters.put("prompt", input)
        requestParameters.put("max_tokens", 150)
        requestParameters.put("temperature", 0.7)
        requestParameters.put("top_p", 1)
        requestParameters.put("frequency_penalty", 0.5)
        requestParameters.put("presence_penalty", 0)
        requestParameters.put("stop", "You")

        val body = requestParameters.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/completions")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val interaction = JSONObject(responseBody)
                    val completions = interaction.getJSONArray("choices")
                    if (completions.length() > 0) {
                        val text = completions.getJSONObject(0).getString("text")

                        runOnUiThread {
                            speak(text)
                        }
                    }
                }
            }
        })
    }

    private suspend fun promptChatGpt(inputText: String) {
        val openAI = OpenAI(token = "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")

        val text = withContext(Dispatchers.IO) {
            val davinci = openAI.model(modelId = ModelId("text-davinci-003"))

            val completionRequest = CompletionRequest(
                model = davinci.id,
                prompt = inputText
            )

            openAI.completion(completionRequest).choices[0].text
        }

        speak(text)
    }

    private fun speak(text: String) {
        printOutput(text)

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun printInput(text: String) {
        printText("You", text)
    }

    private fun printOutput(text: String) {
        printText("Chat GPT", text)
    }

    private fun printText(speakerName: String, text: String) {
        var speakerNameSpan = SpannableString(speakerName)
        speakerNameSpan.setSpan(ForegroundColorSpan(Color.BLUE), 0, speakerNameSpan.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        speakerNameSpan.setSpan(StyleSpan(Typeface.BOLD), 0, speakerNameSpan.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

        var textSpan = SpannableString(text)
        textSpan.setSpan(ForegroundColorSpan(Color.DKGRAY), 0, textSpan.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        textSpan.setSpan(StyleSpan(Typeface.BOLD), 0, textSpan.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

        conversationView.text = conversationHistory
            .append(speakerNameSpan)
            .append("\n")
            .append(textSpan)
            .append("\n")
            .append("\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.stopListening()
        speechRecognizer.destroy()
        tts.shutdown()
    }
}