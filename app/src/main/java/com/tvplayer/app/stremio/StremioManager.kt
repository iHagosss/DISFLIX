package com.tvplayer.app.stremio

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.stremio.core.Core
import com.stremio.core.Env
import com.stremio.core.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StremioManager(private val storage: StremioStorage) {
    private val TAG = "StremioManager"
    private var core: Core? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _catalogs = MutableStateFlow<List<JsonObject>>(emptyList())
    val catalogs: StateFlow<List<JsonObject>> = _catalogs

    private val _metaDetails = MutableStateFlow<JsonObject?>(null)
    val metaDetails: StateFlow<JsonObject?> = _metaDetails

    private val _streams = MutableStateFlow<List<JsonObject>>(emptyList())
    val streams: StateFlow<List<JsonObject>> = _streams

    fun initialize() {
        coroutineScope.launch {
            try {
                Log.d(TAG, "Initializing Stremio Core...")
                
                // Create environment
                val env = Env()
                
                // Initialize Core with storage
                core = Core.initialize(storage, env)
                
                _isReady.value = true
                Log.d(TAG, "Stremio Core initialized successfully")
                
                // Load default addons
                loadDefaultAddons()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Stremio Core", e)
                _isReady.value = false
            }
        }
    }

    private suspend fun loadDefaultAddons() {
        try {
            // Add default Stremio addons
            val defaultAddons = listOf(
                "https://v3-cinemeta.strem.io/manifest.json",
                "https://v3-channels.strem.io/manifest.json"
            )
            
            for (addonUrl in defaultAddons) {
                installAddon(addonUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading default addons", e)
        }
    }

    fun loadCatalog(type: String, catalogId: String = "top", extra: Map<String, String> = emptyMap()) {
        coroutineScope.launch {
            try {
                if (!_isReady.value || core == null) {
                    Log.w(TAG, "Core not ready")
                    return@launch
                }

                Log.d(TAG, "Loading catalog: type=$type, id=$catalogId")
                
                // Dispatch catalog load action
                val action = JsonObject().apply {
                    addProperty("type", "Load")
                    add("args", JsonObject().apply {
                        addProperty("model", "CatalogsWithExtra")
                        add("args", JsonObject().apply {
                            addProperty("type", type)
                            addProperty("id", catalogId)
                            add("extra", gson.toJsonTree(extra))
                        })
                    })
                }
                
                core?.dispatch(action.toString(), "ctx")
                
                // Get catalog state
                delay(500) // Wait for state update
                val state = core?.getState<String>("ctx.content.catalogs")
                if (state != null) {
                    val catalogs = parseCatalogs(state)
                    _catalogs.value = catalogs
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading catalog", e)
            }
        }
    }

    fun loadMetaDetails(type: String, id: String) {
        coroutineScope.launch {
            try {
                if (!_isReady.value || core == null) {
                    Log.w(TAG, "Core not ready")
                    return@launch
                }

                Log.d(TAG, "Loading meta details: type=$type, id=$id")
                
                // Dispatch meta load action
                val action = JsonObject().apply {
                    addProperty("type", "Load")
                    add("args", JsonObject().apply {
                        addProperty("model", "MetaDetails")
                        add("args", JsonObject().apply {
                            addProperty("type", type)
                            addProperty("id", id)
                        })
                    })
                }
                
                core?.dispatch(action.toString(), "ctx")
                
                // Get meta state
                delay(500) // Wait for state update
                val state = core?.getState<String>("ctx.content.metaDetails")
                if (state != null) {
                    val meta = JsonParser.parseString(state).asJsonObject
                    _metaDetails.value = meta
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading meta details", e)
            }
        }
    }

    fun loadStreams(type: String, id: String) {
        coroutineScope.launch {
            try {
                if (!_isReady.value || core == null) {
                    Log.w(TAG, "Core not ready")
                    return@launch
                }

                Log.d(TAG, "Loading streams: type=$type, id=$id")
                
                // Dispatch streams load action
                val action = JsonObject().apply {
                    addProperty("type", "Load")
                    add("args", JsonObject().apply {
                        addProperty("model", "Streams")
                        add("args", JsonObject().apply {
                            addProperty("type", type)
                            addProperty("id", id)
                        })
                    })
                }
                
                core?.dispatch(action.toString(), "player")
                
                // Get streams state
                delay(1000) // Wait for state update
                val state = core?.getState<String>("player.streams")
                if (state != null) {
                    val streams = parseStreams(state)
                    _streams.value = streams
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading streams", e)
            }
        }
    }

    suspend fun installAddon(manifestUrl: String): Boolean {
        return try {
            if (!_isReady.value || core == null) {
                Log.w(TAG, "Core not ready")
                return false
            }

            Log.d(TAG, "Installing addon: $manifestUrl")
            
            val action = JsonObject().apply {
                addProperty("type", "InstallAddon")
                add("args", JsonObject().apply {
                    addProperty("transportUrl", manifestUrl)
                })
            }
            
            core?.dispatch(action.toString(), "ctx")
            delay(500)
            
            Log.d(TAG, "Addon installed: $manifestUrl")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error installing addon", e)
            false
        }
    }

    suspend fun uninstallAddon(transportUrl: String): Boolean {
        return try {
            if (!_isReady.value || core == null) {
                Log.w(TAG, "Core not ready")
                return false
            }

            Log.d(TAG, "Uninstalling addon: $transportUrl")
            
            val action = JsonObject().apply {
                addProperty("type", "UninstallAddon")
                add("args", JsonObject().apply {
                    addProperty("transportUrl", transportUrl)
                })
            }
            
            core?.dispatch(action.toString(), "ctx")
            delay(500)
            
            Log.d(TAG, "Addon uninstalled: $transportUrl")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error uninstalling addon", e)
            false
        }
    }

    fun getInstalledAddons(): List<JsonObject> {
        return try {
            if (!_isReady.value || core == null) {
                Log.w(TAG, "Core not ready")
                return emptyList()
            }

            val state = core?.getState<String>("ctx.addons")
            if (state != null) {
                parseAddons(state)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed addons", e)
            emptyList()
        }
    }

    fun updatePlayerPosition(time: Long, duration: Long) {
        coroutineScope.launch {
            try {
                if (!_isReady.value || core == null) return@launch

                val action = JsonObject().apply {
                    addProperty("type", "TimeChanged")
                    add("args", JsonObject().apply {
                        addProperty("time", time)
                        addProperty("duration", duration)
                    })
                }
                
                core?.dispatch(action.toString(), "player")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating player position", e)
            }
        }
    }

    fun notifyPlaybackEnded() {
        coroutineScope.launch {
            try {
                if (!_isReady.value || core == null) return@launch

                val action = JsonObject().apply {
                    addProperty("type", "Ended")
                }
                
                core?.dispatch(action.toString(), "player")
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying playback ended", e)
            }
        }
    }

    private fun parseCatalogs(json: String): List<JsonObject> {
        return try {
            val element = JsonParser.parseString(json)
            if (element.isJsonArray) {
                element.asJsonArray.map { it.asJsonObject }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing catalogs", e)
            emptyList()
        }
    }

    private fun parseStreams(json: String): List<JsonObject> {
        return try {
            val element = JsonParser.parseString(json)
            if (element.isJsonArray) {
                element.asJsonArray.map { it.asJsonObject }
            } else if (element.isJsonObject) {
                val obj = element.asJsonObject
                if (obj.has("streams") && obj.get("streams").isJsonArray) {
                    obj.getAsJsonArray("streams").map { it.asJsonObject }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing streams", e)
            emptyList()
        }
    }

    private fun parseAddons(json: String): List<JsonObject> {
        return try {
            val element = JsonParser.parseString(json)
            if (element.isJsonArray) {
                element.asJsonArray.map { it.asJsonObject }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing addons", e)
            emptyList()
        }
    }

    fun decodeStreamData(streamJson: String): JsonObject? {
        return try {
            JsonParser.parseString(streamJson).asJsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding stream data", e)
            null
        }
    }

    fun shutdown() {
        coroutineScope.cancel()
        core = null
        _isReady.value = false
        Log.d(TAG, "StremioManager shutdown")
    }
}
