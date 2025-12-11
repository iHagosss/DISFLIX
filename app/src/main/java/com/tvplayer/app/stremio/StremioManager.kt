package com.tvplayer.app.stremio

import android.content.Context
import android.util.Log
import com.stremio.core.Core
import com.stremio.core.Field
import com.stremio.core.models.CatalogsWithExtra
import com.stremio.core.models.Ctx
import com.stremio.core.models.MetaDetails
import com.stremio.core.models.Player
import com.stremio.core.runtime.RuntimeEvent
import com.stremio.core.runtime.msg.Action
import com.stremio.core.runtime.msg.ActionCtx
import com.stremio.core.runtime.msg.ActionLoad
import com.stremio.core.runtime.msg.ActionPlayer
import com.stremio.core.types.addon.Descriptor
import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StremioManager(private val context: Context) {

    companion object {
        private const val TAG = "StremioManager"
        private const val CINEMETA_MANIFEST = "https://v3-cinemeta.strem.io/manifest.json"
    }

    private val storage = StremioStorage(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isInitialized = false
    private var isContextLoaded = false
    private val eventListeners = mutableListOf<StremioEventListener>()

    private val coreEventListener = Core.EventListener { event ->
        Log.d(TAG, "Core event received: ${event.javaClass.simpleName}")
        handleCoreEvent(event)
        notifyEventListeners(event)
    }

    interface StremioEventListener {
        fun onCoreEvent(event: RuntimeEvent)
        fun onContextLoaded()
        fun onInitialized()
        fun onError(error: String)
    }

    private fun handleCoreEvent(event: RuntimeEvent) {
        when {
            event.ctxLoaded != null -> {
                isContextLoaded = true
                Log.i(TAG, "Context loaded")
                installDefaultAddonsIfNeeded()
                loadLibrary()
                notifyContextLoaded()
            }
            event.addonDetailsResult != null -> {
                Log.i(TAG, "Addon details loaded")
                event.addonDetailsResult?.descriptor?.let { descriptor ->
                    if (descriptor.manifest != null) {
                        Log.i(TAG, "Installing addon: ${descriptor.manifest?.name}")
                        installAddonWithDescriptor(descriptor)
                    }
                }
            }
            event.addonInstalled != null -> {
                Log.i(TAG, "Addon installed: ${event.addonInstalled}")
                loadLibrary()
            }
            event.error != null -> {
                Log.e(TAG, "Core error: ${event.error}")
            }
        }
    }

    private fun installDefaultAddonsIfNeeded() {
        try {
            val addons = getInstalledAddons()
            val hasCinemeta = addons.any { it.transportUrl.contains("cinemeta") }

            if (!hasCinemeta) {
                Log.i(TAG, "Loading default Cinemeta addon details")
                loadAddonDetails(CINEMETA_MANIFEST)
            } else {
                Log.d(TAG, "Cinemeta addon already installed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/installing default addons", e)
        }
    }

    private fun loadAddonDetails(transportUrl: String) {
        try {
            val action = Action(
                load = ActionLoad(
                    addonDetails = ActionLoad.AddonDetails(
                        selected = ActionLoad.AddonDetails.Selected(
                            transportUrl = transportUrl
                        )
                    )
                )
            )
            Core.dispatch(action, Field.AddonDetails)
            Log.d(TAG, "Dispatched addon details load for $transportUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading addon details", e)
        }
    }

    fun initialize() {
        if (isInitialized) return

        try {
            val error = Core.initialize(storage)
            if (error != null) {
                Log.e(TAG, "Core initialization error: ${error.message}")
                notifyError(error.message ?: "Unknown initialization error")
                return
            }

            Core.addEventListener(coreEventListener)

            loadContext()

            isInitialized = true
            notifyInitialized()
            Log.i(TAG, "Stremio Core initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Stremio Core", e)
            notifyError(e.message ?: "Initialization failed")
        }
    }

    private fun loadContext() {
        try {
            val action = Action(
                load = ActionLoad(
                    ctx = ActionLoad.Ctx()
                )
            )
            Core.dispatch(action, null)
            Log.d(TAG, "Dispatched LoadCtx action")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading context", e)
        }
    }

    private fun loadLibrary() {
        try {
            val action = Action(
                load = ActionLoad(
                    libraryByType = ActionLoad.LibraryByType()
                )
            )
            Core.dispatch(action, Field.LibraryByType)
            Log.d(TAG, "Dispatched LoadLibrary action")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading library", e)
        }
    }

    fun addEventListener(listener: StremioEventListener) {
        eventListeners.add(listener)
    }

    fun removeEventListener(listener: StremioEventListener) {
        eventListeners.remove(listener)
    }

    private fun notifyEventListeners(event: RuntimeEvent) {
        scope.launch {
            eventListeners.forEach { it.onCoreEvent(event) }
        }
    }

    private fun notifyContextLoaded() {
        scope.launch {
            eventListeners.forEach { it.onContextLoaded() }
        }
    }

    private fun notifyInitialized() {
        scope.launch {
            eventListeners.forEach { it.onInitialized() }
        }
    }

    private fun notifyError(error: String) {
        scope.launch {
            eventListeners.forEach { it.onError(error) }
        }
    }

    fun getContext(): Ctx? {
        return try {
            Core.getState<Ctx>(Field.Ctx)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting context", e)
            null
        }
    }

    fun isReady(): Boolean = isInitialized && isContextLoaded

    fun getInstalledAddons(): List<Descriptor> {
        return try {
            val ctx = getContext()
            ctx?.profile?.addons ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting addons", e)
            emptyList()
        }
    }

    fun findAddonForResource(resource: String, type: String): Descriptor? {
        return getInstalledAddons().find { descriptor ->
            descriptor.manifest?.resources?.any { res ->
                res.name == resource && (res.types?.contains(type) == true || res.types == null)
            } == true
        }
    }

    fun loadCatalog(type: String, catalogId: String, addon: Descriptor? = null) {
        try {
            val targetAddon = addon ?: findAddonForResource("catalog", type)
            if (targetAddon == null) {
                Log.w(TAG, "No addon found for catalog type: $type")
                return
            }

            val request = ResourceRequest(
                base = targetAddon.transportUrl,
                path = ResourcePath(
                    resource = "catalog",
                    type_ = type,
                    id = catalogId
                )
            )

            val action = Action(
                load = ActionLoad(
                    catalogsWithExtra = ActionLoad.CatalogsWithExtra(
                        selected = CatalogsWithExtra.Selected(request = request)
                    )
                )
            )

            Core.dispatch(action, Field.CatalogsWithExtra)
            Log.d(TAG, "Dispatched catalog load for $type/$catalogId")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading catalog", e)
        }
    }

    fun getCatalogsWithExtra(): CatalogsWithExtra? {
        return try {
            Core.getState<CatalogsWithExtra>(Field.CatalogsWithExtra)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting catalogs", e)
            null
        }
    }

    fun loadMetaDetails(type: String, id: String, videoId: String? = null, addon: Descriptor? = null) {
        try {
            val targetAddon = addon ?: findAddonForResource("meta", type)
            if (targetAddon == null) {
                Log.w(TAG, "No addon found for meta type: $type")
                return
            }

            val metaPath = ResourcePath(
                resource = "meta",
                type_ = type,
                id = id
            )

            val streamPath = videoId?.let {
                ResourcePath(
                    resource = "stream",
                    type_ = type,
                    id = it
                )
            }

            val action = Action(
                load = ActionLoad(
                    metaDetails = ActionLoad.MetaDetails(
                        selected = MetaDetails.Selected(
                            metaPath = metaPath,
                            streamPath = streamPath
                        )
                    )
                )
            )

            Core.dispatch(action, Field.MetaDetails)
            Log.d(TAG, "Dispatched meta details load for $type/$id")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading meta details", e)
        }
    }

    fun getMetaDetails(): MetaDetails? {
        return try {
            Core.getState<MetaDetails>(Field.MetaDetails)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting meta details", e)
            null
        }
    }

    fun loadPlayer(stream: Stream, type: String, metaId: String, videoId: String?) {
        try {
            val addon = findAddonForResource("meta", type)

            val metaRequest = addon?.let {
                ResourceRequest(
                    base = it.transportUrl,
                    path = ResourcePath(
                        resource = "meta",
                        type_ = type,
                        id = metaId
                    )
                )
            }

            val streamRequest = videoId?.let { vid ->
                addon?.let {
                    ResourceRequest(
                        base = it.transportUrl,
                        path = ResourcePath(
                            resource = "stream",
                            type_ = type,
                            id = vid
                        )
                    )
                }
            }

            val action = Action(
                load = ActionLoad(
                    player = ActionLoad.Player(
                        selected = Player.Selected(
                            stream = stream,
                            metaRequest = metaRequest,
                            streamRequest = streamRequest
                        )
                    )
                )
            )

            Core.dispatch(action, Field.Player)
            Log.d(TAG, "Dispatched player load")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading player", e)
        }
    }

    fun getPlayer(): Player? {
        return try {
            Core.getState<Player>(Field.Player)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting player state", e)
            null
        }
    }

    fun decodeStreamData(streamData: String): Stream? {
        return try {
            Core.decodeStreamData(streamData)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding stream data", e)
            null
        }
    }

    fun installAddon(transportUrl: String) {
        loadAddonDetails(transportUrl)
    }

    private fun installAddonWithDescriptor(descriptor: Descriptor) {
        try {
            val action = Action(
                ctx = ActionCtx(
                    installAddon = ActionCtx.InstallAddon(
                        descriptor = descriptor
                    )
                )
            )

            Core.dispatch(action, null)
            Log.d(TAG, "Dispatched addon install for ${descriptor.transportUrl}")
        } catch (e: Exception) {
            Log.e(TAG, "Error installing addon", e)
        }
    }

    fun uninstallAddon(transportUrl: String) {
        try {
            val action = Action(
                ctx = ActionCtx(
                    uninstallAddon = ActionCtx.UninstallAddon(
                        descriptor = Descriptor(
                            transportUrl = transportUrl,
                            manifest = null
                        )
                    )
                )
            )

            Core.dispatch(action, null)
            Log.d(TAG, "Dispatched addon uninstall for $transportUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error uninstalling addon", e)
        }
    }

    fun updatePlayerTime(currentTime: Long, duration: Long) {
        try {
            val action = Action(
                player = ActionPlayer(
                    timeChanged = ActionPlayer.TimeChanged(
                        time = currentTime,
                        duration = duration,
                        device = "android"
                    )
                )
            )

            Core.dispatch(action, Field.Player)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating player time", e)
        }
    }

    fun updatePlayerPaused(paused: Boolean) {
        try {
            val action = Action(
                player = ActionPlayer(
                    pausedChanged = ActionPlayer.PausedChanged(paused = paused)
                )
            )

            Core.dispatch(action, Field.Player)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating player paused state", e)
        }
    }

    fun shutdown() {
        try {
            Core.removeEventListener(coreEventListener)
            scope.cancel()
            isInitialized = false
            isContextLoaded = false
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
}
