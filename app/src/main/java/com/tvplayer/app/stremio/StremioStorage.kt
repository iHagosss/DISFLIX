package com.tvplayer.app.stremio

import android.content.Context
import android.content.SharedPreferences
import com.stremio.core.Storage
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class StremioStorage(context: Context) : Storage {

    companion object {
        private const val PREFS_NAME = "stremio_core_storage"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = ReentrantReadWriteLock()

    override fun get(key: String): Storage.Result<String?> {
        return lock.read {
            try {
                val value = prefs.getString(key, null)
                Storage.Result.Ok(value)
            } catch (e: Exception) {
                Storage.Result.Err(e.message ?: "Unknown error reading key: $key")
            }
        }
    }

    override fun set(key: String, value: String?): Storage.Result<Unit> {
        return lock.write {
            try {
                val editor = prefs.edit()
                if (value == null) {
                    editor.remove(key)
                } else {
                    editor.putString(key, value)
                }
                val success = editor.commit()
                if (success) {
                    Storage.Result.Ok(Unit)
                } else {
                    Storage.Result.Err("Failed to commit key: $key")
                }
            } catch (e: Exception) {
                Storage.Result.Err(e.message ?: "Unknown error writing key: $key")
            }
        }
    }
}
