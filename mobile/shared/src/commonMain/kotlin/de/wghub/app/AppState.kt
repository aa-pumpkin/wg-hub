package de.wghub.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.wghub.app.data.ApiException
import de.wghub.app.data.HouseholdDetail
import de.wghub.app.data.HouseholdListItem
import de.wghub.app.data.HouseholdsResponse
import de.wghub.app.data.Repository
import de.wghub.app.data.User
import de.wghub.app.data.UserResponse
import de.wghub.app.data.json
import de.wghub.app.i18n.Texts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface Screen {
    data object Loading : Screen
    data object Login : Screen
    data class PickHousehold(val households: List<HouseholdListItem>) : Screen
    data class Main(val detail: HouseholdDetail) : Screen
}

/** Who is signed in, which WG is open, which language: the app-wide state. */
class AppState(val repo: Repository, private val scope: CoroutineScope) {
    var screen by mutableStateOf<Screen>(Screen.Loading)
        private set
    var texts by mutableStateOf<Texts?>(null)
        private set
    var user by mutableStateOf(repo.settings.user)
        private set

    var theme by mutableStateOf(repo.settings.theme)
        private set

    fun chooseTheme(mode: String) {
        theme = mode
        repo.settings.theme = mode
    }

    /** Messages for the snackbar. */
    var onMessage: (String) -> Unit = {}

    fun start() = scope.launch {
        texts = Texts.load(repo.settings.user?.locale ?: deviceLanguage())
        if (repo.settings.token == null) {
            screen = Screen.Login
            return@launch
        }
        // Show the last state at once, even without a connection.
        val cached = repo.settings.lastHouseholdId?.let { repo.cached("/households/$it", HouseholdDetail.serializer()) }
        if (cached != null) screen = Screen.Main(cached)
        try {
            val me = json.decodeFromString(UserResponse.serializer(), repo.call("GET", "/auth/me")).user
            applyUser(me)
            if (cached == null) openLanding() else refreshHousehold()
        } catch (error: ApiException) {
            when {
                error.status == 401 -> signOut()
                cached == null && error.isOffline -> screen = Screen.Login
            }
        }
    }

    fun applyUser(next: User) {
        user = next
        repo.settings.user = next
        if (texts?.locale != next.locale) texts = texts?.withLocale(next.locale)
    }

    suspend fun login(server: String, username: String, password: String) {
        repo.settings.serverUrl = server.trim().ifEmpty { de.wghub.app.data.DEFAULT_SERVER }
        val body = buildJsonObject {
            put("username", username.trim())
            put("password", password)
        }.toString()
        val me = json.decodeFromString(UserResponse.serializer(), repo.call("POST", "/auth/login", body)).user
        applyUser(me)
        openLanding()
    }

    suspend fun register(server: String, displayName: String, username: String, password: String) {
        repo.settings.serverUrl = server.trim().ifEmpty { de.wghub.app.data.DEFAULT_SERVER }
        val body = buildJsonObject {
            put("displayName", displayName)
            put("username", username.trim())
            put("password", password)
            put("locale", texts?.locale ?: "de")
        }.toString()
        val me = json.decodeFromString(UserResponse.serializer(), repo.call("POST", "/auth/register", body)).user
        applyUser(me)
        openLanding()
    }

    suspend fun createHousehold(name: String) {
        val response = repo.call("POST", "/households", buildJsonObject { put("name", name) }.toString())
        val id = json.parseToJsonElement(response).jsonObject["household"]!!.jsonObject["id"]!!.jsonPrimitive.content
        openHousehold(id)
    }

    /** Accepts the whole invitation link or the bare code. */
    suspend fun joinHousehold(input: String) {
        val code = input.trim().split("/").lastOrNull { it.isNotEmpty() }.orEmpty()
        val preview = json.parseToJsonElement(repo.call("GET", "/invitations/$code")).jsonObject["invitation"]!!.jsonObject
        val response = repo.call("POST", "/invitations/$code/accept")
        val id = json.parseToJsonElement(response).jsonObject["householdId"]!!.jsonPrimitive.content
        texts?.let { onMessage(it("invite.joined", "name" to preview["householdName"]!!.jsonPrimitive.content)) }
        openHousehold(id)
    }

    /** After leaving or deleting a WG. */
    suspend fun leftHousehold() {
        repo.settings.lastHouseholdId = null
        openLanding()
    }

    /** The last WG, or the first, or the list to choose from. */
    private suspend fun openLanding() {
        val list = repo.fetch("/households", HouseholdsResponse.serializer()).households
        val target = list.firstOrNull { it.id == repo.settings.lastHouseholdId } ?: list.singleOrNull()
        if (target != null) openHousehold(target.id) else screen = Screen.PickHousehold(list)
    }

    suspend fun openHousehold(id: String) {
        val detail = repo.fetch("/households/$id", HouseholdDetail.serializer())
        repo.settings.lastHouseholdId = id
        screen = Screen.Main(detail)
    }

    /** Reloads the open WG, e.g. after a member was frozen. */
    suspend fun refreshHousehold() {
        val current = (screen as? Screen.Main)?.detail ?: return
        try {
            val detail = repo.fetch("/households/${current.household.id}", HouseholdDetail.serializer())
            if ((screen as? Screen.Main)?.detail?.household?.id == detail.household.id) screen = Screen.Main(detail)
        } catch (error: ApiException) {
            if (error.code == "not_found") openLanding()
        }
    }

    fun showWelcome() = showHouseholds()

    fun showHouseholds() = scope.launch {
        val list = repo.cached("/households", HouseholdsResponse.serializer())?.households.orEmpty()
        screen = Screen.PickHousehold(list)
        try {
            screen = Screen.PickHousehold(repo.fetch("/households", HouseholdsResponse.serializer()).households)
        } catch (_: ApiException) {
        }
    }

    suspend fun setLocale(locale: String) {
        val body = buildJsonObject { put("locale", locale) }.toString()
        applyUser(json.decodeFromString(UserResponse.serializer(), repo.call("PATCH", "/me", body)).user)
    }

    fun signOut() = scope.launch {
        runCatching { repo.call("POST", "/auth/logout") }
        repo.signOut()
        user = null
        screen = Screen.Login
    }

    /** The translated text for an API error code. */
    fun errorText(error: Throwable): String {
        val t = texts ?: return error.message ?: ""
        val code = (error as? ApiException)?.code ?: "internal_error"
        return if (t.has("errors.$code")) t("errors.$code") else t("errors.internal_error")
    }
}

expect fun deviceLanguage(): String
