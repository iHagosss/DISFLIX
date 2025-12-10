package com.tvplayer.app

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.tvplayer.app.models.SkipMarkers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class ApiService {
    private val client: OkHttpClient = OkHttpClient()
    private val gson: Gson = Gson()

    interface SkipMarkersCallback {
        fun onSuccess(markers: SkipMarkers)
        fun onError(e: Exception)
    }

    fun fetchSkipMarkers(url: String, callback: SkipMarkersCallback) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post { callback.onError(e) }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected HTTP code: $response")
                    }

                    val jsonData = response.body?.string()
                    val markers: SkipMarkers = gson.fromJson(jsonData, SkipMarkers::class.java)

                    Handler(Looper.getMainLooper()).post { callback.onSuccess(markers) }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post { callback.onError(e) }
                } finally {
                    response.close()
                }
            }
        })
    }
}
