package com.example.dictationdaddy.network

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File

// Whisper call
object WhisperService {

    private const val GROQ_API_KEY = "YOUR_API_KEY" // replace with real key
    private const val WHISPER_URL = "https://api.groq.com/openai/v1/audio/transcriptions"

    fun transcribe(file: File, callback: (String?) -> Unit) {
        val client = OkHttpClient()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name,
                RequestBody.create("audio/m4a".toMediaTypeOrNull(), file))
            .addFormDataPart("model", "whisper-large-v3")
            .build()

        val request = Request.Builder()
            .url(WHISPER_URL)
            .addHeader("Authorization", "Bearer $GROQ_API_KEY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e("WhisperService", "Error: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val text = json.optString("text")
                    callback(text)
                } else {
                    Log.e("WhisperService", "Failed: $body")
                    callback(null)
                }
            }
        })
    }
}
