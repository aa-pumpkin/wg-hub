package de.wghub.app.ui.dishwasher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.AppState
import de.wghub.app.LocalApp
import de.wghub.app.data.ApiException
import de.wghub.app.data.DishwasherState
import de.wghub.app.data.PendingAction
import de.wghub.app.data.json
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.ui.Avatar
import de.wghub.app.ui.Card
import de.wghub.app.ui.CardList
import de.wghub.app.ui.Hint
import de.wghub.app.ui.ListRow
import de.wghub.app.ui.Loaded
import de.wghub.app.ui.Loader
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.ScreenColumn
import de.wghub.app.ui.Wg
import de.wghub.app.ui.attempt
import kotlinx.coroutines.launch
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/** The dishwasher counter, also usable offline. Mirrors frontend/src/composables/useDishwasher.ts. */
class DishwasherModel(private val app: AppState, private val householdId: String) {
    private val path = "/households/$householdId/dishwasher"
    val loader = Loader(app, path, DishwasherState.serializer())
    var busy by mutableStateOf(false)
        private set

    /** The counter with taps that are still waiting to be sent. */
    fun state(): DishwasherState? {
        val state = loader.value ?: return null
        val waiting = app.repo.pending.value.count { it.householdId == householdId && it.type == "dishwasher-tap" }
        if (waiting == 0) return state
        val me = app.user?.id
        return state.copy(
            total = state.total + waiting,
            members = state.members.map { if (it.user.id == me) it.copy(count = it.count + waiting) else it },
        )
    }

    suspend fun load() = loader.load()

    suspend fun tap() {
        if (busy) return
        busy = true
        val action = PendingAction(Random.nextLong().toULong().toString(16), "POST", "$path/entries", null, "dishwasher-tap", householdId)
        try {
            val response = app.repo.sendOrQueue(action)
            if (response == null) app.onMessage(app.texts!!("app.queued"))
            else {
                val result = json.parseToJsonElement(response).jsonObject
                val t = app.texts!!
                if (result["crown"]?.jsonPrimitive?.boolean == true) app.onMessage(t("dishwasher.crownWon"))
                else if (result["finished"]?.jsonPrimitive?.boolean == true) {
                    app.onMessage(t("dishwasher.crownOf", "name" to (result["winnerName"]?.jsonPrimitive?.content ?: "")))
                }
            }
        } catch (error: ApiException) {
            app.onMessage(app.errorText(error))
        }
        load()
        busy = false
    }

    suspend fun undo() {
        app.attempt { app.repo.call("DELETE", "$path/entries/last") }
        load()
    }
}

/** Mirrors frontend/src/views/DishwasherView.vue. */
@Composable
fun DishwasherScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val model = remember(household.id) { DishwasherModel(app, household.id) }
    app.repo.pending.collectAsState().value
    LaunchedEffect(model, household.refreshTick) { model.load() }

    ScreenColumn(onRefresh = { model.load() }) {
        Loaded(model.loader, { scope.launch { model.load() } }) { _ ->
            val state = model.state()!!
            val mine = state.members.firstOrNull { it.user.id == app.user?.id }
            val crownName = state.members.firstOrNull { it.user.id == state.crownHolderId }?.user?.displayName
            val fraction = (state.total.toFloat() / (state.target ?: 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            Card {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Hint(t("dishwasher.round", "cycle" to state.cycle))
                    Text("${state.total} / ${state.target ?: 0}", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.fillMaxWidth().height(8.dp).background(Wg.colors.subtle, RoundedCornerShape(50))) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Wg.colors.accent, RoundedCornerShape(50)))
                    }
                    if (crownName != null) Text(t("dishwasher.crownOf", "name" to crownName), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Button(
                        onClick = { scope.launch { model.tap() } },
                        enabled = !model.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 4.dp),
                    ) { Text("+1 · ${t("dishwasher.tap")}", fontSize = 16.sp) }
                    if ((mine?.count ?: 0) > 0) {
                        Text(
                            t("dishwasher.undo"),
                            fontSize = 13.sp,
                            color = Wg.colors.muted,
                            modifier = Modifier.clickable { scope.launch { model.undo() } }.padding(10.dp),
                        )
                    }
                }
            }
            CardList(state.members.sortedByDescending { it.count }) { row ->
                ListRow(Modifier.alpha(if (row.frozen) 0.6f else 1f)) {
                    Avatar(row.user)
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.user.displayName +
                                (if (row.user.id == app.user?.id) " (${t("common.you")})" else "") +
                                (if (row.user.id == state.crownHolderId) " 👑" else ""),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        val extra = listOfNotNull(
                            if (row.frozen) t("members.frozen") else null,
                            if (row.crowns > 0) t("dishwasher.crowns", "count" to row.crowns) else null,
                            if (row.strikes > 0) t("dishwasher.strikes", "count" to row.strikes) else null,
                        )
                        if (extra.isNotEmpty()) Text(extra.joinToString(" · "), fontSize = 13.sp, color = Wg.colors.muted)
                    }
                    Text("${row.count}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
                }
            }
            state.lastCycle?.takeIf { it.struck.isNotEmpty() }?.let { last ->
                Text(
                    t("dishwasher.lastStruck", "names" to last.struck.joinToString(", ") { it.displayName }, "points" to state.strikePenalty),
                    fontSize = 14.sp,
                    color = Wg.colors.warn,
                )
            }
            Hint(t("dishwasher.rule", "target" to (state.target ?: 0), "points" to state.strikePenalty))
        }
    }
}
