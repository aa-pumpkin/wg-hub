package de.wghub.app.ui.chores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.wghub.app.AppState
import de.wghub.app.data.ApiException
import de.wghub.app.data.Chore
import de.wghub.app.data.ChoreInput
import de.wghub.app.data.ChorePlan
import de.wghub.app.data.ChoresResponse
import de.wghub.app.data.CompletionResult
import de.wghub.app.data.Member
import de.wghub.app.data.Occurrence
import de.wghub.app.data.OccurrencesResponse
import de.wghub.app.data.PendingAction
import de.wghub.app.data.json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Chores of one WG: what the website's chore store and plan panel load
 * (frontend/src/stores/chores.ts), plus changes still waiting to be sent.
 */
class ChoresModel(private val app: AppState, private val householdId: String) {
    private val repo = app.repo
    private val base = "/households/$householdId"
    private val seriesPath = "$base/chores"
    private val openPath = "$base/chore-occurrences?status=PENDING&limit=300"
    private val historyPath = "$base/chore-occurrences?status=COMPLETED&limit=50"
    private val planPath = "$base/chore-plan"

    var series by mutableStateOf(repo.cached(seriesPath, ChoresResponse.serializer())?.chores.orEmpty())
        private set
    private var serverOpen by mutableStateOf(repo.cached(openPath, OccurrencesResponse.serializer())?.occurrences)
    private var serverHistory by mutableStateOf(repo.cached(historyPath, OccurrencesResponse.serializer())?.occurrences.orEmpty())
    var plan by mutableStateOf(repo.cached(planPath, ChorePlan.serializer()))
        private set
    /** True when nothing could be loaded, not even an old copy. */
    var failed by mutableStateOf(false)
        private set
    /** Rows hidden right away while their change is on its way. */
    private var hidden by mutableStateOf(emptySet<String>())

    val loaded get() = serverOpen != null

    /** Open tasks as the user should see them: with unsent changes already applied. */
    fun open(members: List<Member>): List<Occurrence> {
        val waiting = repo.pending.value.filter { it.householdId == householdId }
        val gone = hidden + waiting.filter { it.type == "complete" || it.type == "skip" }.mapNotNull { it.targetId }
        val reassigned = waiting.filter { it.type == "reassign" }.associate { it.targetId to it.value }
        return serverOpen.orEmpty().filter { it.id !in gone }.map { item ->
            if (item.id !in reassigned) item
            else item.copy(assignee = members.firstOrNull { it.id == reassigned[item.id] }?.let {
                de.wghub.app.data.UserSummary(it.id, it.displayName, it.avatarColor)
            })
        }
    }

    fun history(): List<Occurrence> {
        val reopened = repo.pending.value.filter { it.householdId == householdId && it.type == "reopen" }.mapNotNull { it.targetId }
        return serverHistory.filter { it.id !in reopened && it.id !in hidden }
    }

    suspend fun load() {
        try {
            coroutineScope {
                val chores = async { repo.fetch(seriesPath, ChoresResponse.serializer()).chores }
                val open = async { repo.fetch(openPath, OccurrencesResponse.serializer()).occurrences }
                val done = async { repo.fetch(historyPath, OccurrencesResponse.serializer()).occurrences }
                val nextPlan = async { repo.fetch(planPath, ChorePlan.serializer()) }
                series = chores.await()
                serverOpen = open.await()
                serverHistory = done.await()
                plan = nextPlan.await()
            }
            hidden = emptySet()
            failed = false
        } catch (error: ApiException) {
            failed = serverOpen == null
            if (!error.isOffline && serverOpen != null) app.onMessage(app.errorText(error))
        }
    }

    private fun action(type: String, method: String, path: String, target: String, body: String? = null, value: String? = null) =
        PendingAction(
            id = Random.nextLong().toULong().toString(16),
            method = method,
            path = path,
            body = body,
            type = type,
            householdId = householdId,
            targetId = target,
            value = value,
        )

    /**
     * Sends a tick-off style change. Without a connection it is kept and sent later;
     * the row changes at once either way.
     */
    private suspend fun quick(item: Occurrence, pending: PendingAction): String? {
        hidden = hidden + item.id
        return try {
            val response = repo.sendOrQueue(pending)
            if (response == null) app.onMessage(app.texts!!("app.queued"))
            response
        } catch (error: ApiException) {
            hidden = hidden - item.id
            app.onMessage(app.errorText(error))
            null
        } finally {
            load()
        }
    }

    suspend fun complete(item: Occurrence) {
        val response = quick(item, action("complete", "POST", "$base/chore-occurrences/${item.id}/complete", item.id))
            ?: return
        val result = json.decodeFromString(CompletionResult.serializer(), response)
        val t = app.texts!!
        if (result.earned > 0) app.onMessage(t("points.earned", "points" to result.earned))
        if (result.leveledUp) {
            app.onMessage(
                t("points.levelUp", "level" to result.level, "title" to t("points.levels.l${minOf(result.level, 10)}")),
            )
        }
    }

    suspend fun skip(item: Occurrence) {
        quick(item, action("skip", "POST", "$base/chore-occurrences/${item.id}/skip", item.id))
    }

    suspend fun reopen(item: Occurrence) {
        quick(item, action("reopen", "POST", "$base/chore-occurrences/${item.id}/reopen", item.id))
    }

    suspend fun reassign(item: Occurrence, assigneeId: String?) {
        val body = buildJsonObject { put("assigneeId", assigneeId?.let(::JsonPrimitive) ?: JsonNull) }.toString()
        try {
            val sent = repo.sendOrQueue(
                action("reassign", "PATCH", "$base/chore-occurrences/${item.id}", item.id, body, assigneeId),
            )
            if (sent == null) app.onMessage(app.texts!!("app.queued"))
        } catch (error: ApiException) {
            app.onMessage(app.errorText(error))
        }
        load()
    }

    // The changes below need the server: they reshape the plan for everyone.

    /** Returns field errors from the server, empty when saved. */
    suspend fun save(chore: Chore?, input: ChoreInput): Map<String, String> {
        val body = json.encodeToString(ChoreInput.serializer(), input)
        try {
            if (chore == null) repo.call("POST", seriesPath, body)
            else repo.call("PATCH", "$seriesPath/${chore.id}", body)
        } catch (error: ApiException) {
            if (error.fields.isNotEmpty()) return error.fields
            app.onMessage(app.errorText(error))
            return mapOf("" to error.code)
        }
        app.onMessage(app.texts!!("chores.saved"))
        load()
        return emptyMap()
    }

    suspend fun end(chore: Chore) = change {
        repo.call("DELETE", "$seriesPath/${chore.id}")
        app.onMessage(app.texts!!("chores.ended"))
    }

    suspend fun setCircle(userIds: List<String>) = change {
        val body = buildJsonObject {
            put("userIds", kotlinx.serialization.json.JsonArray(userIds.map(::JsonPrimitive)))
        }.toString()
        repo.call("PUT", "$planPath/circle", body)
    }

    suspend fun shift(weeks: Int) = change {
        repo.call("POST", "$planPath/shift", buildJsonObject { put("weeks", weeks) }.toString())
    }

    suspend fun setFrozen(userId: String, frozen: Boolean) = change {
        repo.call("POST", "$base/members/$userId/freeze", buildJsonObject { put("frozen", frozen) }.toString())
        app.refreshHousehold()
    }

    private suspend fun change(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: ApiException) {
            app.onMessage(app.errorText(error))
        }
        load()
    }
}
