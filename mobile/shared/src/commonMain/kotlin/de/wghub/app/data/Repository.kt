package de.wghub.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** The address the app talks to while the server runs on the home PC. */
const val DEFAULT_SERVER = "http://192.168.0.160:8080"

@Serializable
private data class SavedSettings(
    val serverUrl: String = DEFAULT_SERVER,
    val token: String? = null,
    val user: User? = null,
    val lastHouseholdId: String? = null,
    val theme: String = "system",
)

/** What the app remembers between launches. */
class Settings {
    private var saved = FileStore.read("settings.json")
        ?.let { runCatching { json.decodeFromString(SavedSettings.serializer(), it) }.getOrNull() }
        ?: SavedSettings()

    private fun save(next: SavedSettings) {
        saved = next
        FileStore.write("settings.json", json.encodeToString(SavedSettings.serializer(), next))
    }

    var serverUrl: String
        get() = saved.serverUrl
        set(value) = save(saved.copy(serverUrl = value))
    var token: String?
        get() = saved.token
        set(value) = save(saved.copy(token = value))
    var user: User?
        get() = saved.user
        set(value) = save(saved.copy(user = value))
    var lastHouseholdId: String?
        get() = saved.lastHouseholdId
        set(value) = save(saved.copy(lastHouseholdId = value))

    /** "system", "light" or "dark"; kept on this phone only, like on the website. */
    var theme: String
        get() = saved.theme
        set(value) = save(saved.copy(theme = value))

    /** Back to a fresh install, except for the server address. */
    fun reset() = save(SavedSettings(serverUrl = saved.serverUrl, theme = saved.theme))
}

/**
 * A change made while the server could not be reached. It is kept on the phone and sent,
 * in order, as soon as the server answers again. Until then the screens show it as done.
 */
@Serializable
data class PendingAction(
    val id: String,
    val method: String,
    val path: String,
    val body: String? = null,
    val type: String,
    val householdId: String,
    val targetId: String? = null,
    val value: String? = null,
)

/**
 * Every screen reads through here: it shows the last saved copy at once, then the fresh one
 * from the server. Simple changes that fail for lack of a connection wait in [pending].
 */
class Repository {
    val settings = Settings()
    val api = Api(settings)

    private val outboxLock = Mutex()
    private val _pending = MutableStateFlow(readOutbox())
    val pending: StateFlow<List<PendingAction>> = _pending

    private val _offline = MutableStateFlow(false)
    /** True after the last request could not reach the server. */
    val offline: StateFlow<Boolean> = _offline

    private fun cacheName(path: String) = "cache_" + path.hashCode().toUInt().toString(16)

    fun <T> cached(path: String, serializer: KSerializer<T>): T? =
        FileStore.read(cacheName(path))?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    /** Loads from the server and keeps a copy for offline use. */
    suspend fun <T> fetch(path: String, serializer: KSerializer<T>): T {
        val text = call("GET", path)
        val value = json.decodeFromString(serializer, text)
        FileStore.write(cacheName(path), text)
        return value
    }

    /** Any request; notes whether the server was reachable. */
    suspend fun call(method: String, path: String, body: String? = null): String =
        try {
            api.send(method, path, body).also { _offline.value = false }
        } catch (error: ApiException) {
            _offline.value = error.isOffline
            throw error
        }

    /**
     * Sends a change now, or keeps it for later when there is no connection.
     * Returns the response, or null when it was kept for later.
     */
    suspend fun sendOrQueue(action: PendingAction): String? {
        flush()
        if (_pending.value.isEmpty()) {
            try {
                return call(action.method, action.path, action.body)
            } catch (error: ApiException) {
                if (!error.isOffline) throw error
            }
        }
        outboxLock.withLock { saveOutbox(_pending.value + action) }
        return null
    }

    /**
     * Sends waiting changes in order. Stops at the first one that cannot reach the server.
     * Returns the error codes of changes the server refused; those are dropped.
     */
    suspend fun flush(): List<String> = outboxLock.withLock {
        val refused = mutableListOf<String>()
        while (_pending.value.isNotEmpty()) {
            val next = _pending.value.first()
            try {
                call(next.method, next.path, next.body)
            } catch (error: ApiException) {
                if (error.isOffline) break
                refused += error.code
            }
            saveOutbox(_pending.value.drop(1))
        }
        refused
    }

    private fun readOutbox(): List<PendingAction> =
        FileStore.read("outbox.json")
            ?.let { runCatching { json.decodeFromString(ListSerializer(PendingAction.serializer()), it) }.getOrNull() }
            .orEmpty()

    private fun saveOutbox(list: List<PendingAction>) {
        _pending.value = list
        FileStore.write("outbox.json", json.encodeToString(ListSerializer(PendingAction.serializer()), list))
    }

    /** Forgets everything of the signed-in user, but keeps the server address. */
    fun signOut() {
        FileStore.clear()
        _pending.value = emptyList()
        settings.reset()
    }
}
