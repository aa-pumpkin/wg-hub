package de.wghub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.wghub.app.LocalApp
import de.wghub.app.data.ApiException
import de.wghub.app.data.HouseholdDetail
import de.wghub.app.data.HouseholdsResponse
import de.wghub.app.i18n.Format
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.ui.calendar.CalendarScreen
import de.wghub.app.ui.chores.ChoresScreen
import de.wghub.app.ui.dashboard.DashboardScreen
import de.wghub.app.ui.dishwasher.DishwasherScreen
import de.wghub.app.ui.expenses.ExpensesScreen
import de.wghub.app.ui.members.MembersScreen
import de.wghub.app.ui.notes.NotesScreen
import de.wghub.app.ui.points.PointsScreen
import de.wghub.app.ui.settings.ProfileScreen
import de.wghub.app.ui.settings.SettingsScreen
import de.wghub.app.ui.shopping.ShoppingScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The open WG, its members, and a counter that ticks whenever screens should reload. */
class HouseholdScope(val detail: HouseholdDetail, val format: Format, val refreshTick: Int, val refresh: () -> Unit) {
    val id get() = detail.household.id
    val isAdmin get() = detail.isAdmin
    val members get() = detail.members
    /** Members who take part right now (not frozen). */
    val active get() = detail.members.filter { !it.frozen }
}

val LocalHousehold = compositionLocalOf<HouseholdScope> { error("no household") }

enum class Tab { Dashboard, Chores, Shopping, Calendar, More }
enum class Page { Notes, Expenses, Points, Dishwasher, Members, Settings, Profile }

/** Moving between tabs and the pages behind "More". */
class Nav {
    var tab by mutableStateOf(Tab.Dashboard)
    var page by mutableStateOf<Page?>(null)

    fun open(tab: Tab) {
        this.tab = tab
        page = null
    }

    fun open(page: Page) {
        tab = Tab.More
        this.page = page
    }
}

val LocalNav = compositionLocalOf<Nav> { error("no nav") }

@Composable
fun MainScreen(detail: HouseholdDetail) {
    val app = LocalApp.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val nav = remember(detail.household.id) { Nav() }
    var tick by remember { mutableIntStateOf(0) }
    val format = remember(t, detail.household.timezone) { Format(t, detail.household.timezone) }

    fun refresh() {
        scope.launch {
            app.repo.flush().forEach { code -> app.onMessage(app.errorText(ApiException(400, code))) }
            app.refreshHousehold()
            tick++
        }
    }

    // Fresh data whenever the app comes back to the screen, and every half minute while open.
    LifecycleResumeEffect(Unit) {
        refresh()
        val job = scope.launch {
            while (true) {
                delay(30_000)
                refresh()
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    BackHandler(enabled = nav.page != null || nav.tab != Tab.Dashboard) {
        if (nav.page != null) nav.page = null else nav.tab = Tab.Dashboard
    }

    val household = HouseholdScope(detail, format, tick, ::refresh)
    val pageTitle = nav.page?.let { page -> pageTitle(page, t) }

    CompositionLocalProvider(LocalHousehold provides household, LocalNav provides nav) {
        Scaffold(
            containerColor = Wg.colors.bg,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (nav.page != null) {
                            IconButton(onClick = { nav.page = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("common.backToApp")) }
                        }
                    },
                    title = {
                        if (pageTitle != null) {
                            Text(pageTitle, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(detail.household.icon, fontSize = 20.sp)
                                Text(
                                    detail.household.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Wg.colors.bg),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Wg.colors.surface) {
                    listOf(
                        Triple(Tab.Dashboard, Icons.Default.Home, "nav.dashboard"),
                        Triple(Tab.Chores, Icons.Default.Checklist, "nav.chores"),
                        Triple(Tab.Shopping, Icons.Default.ShoppingCart, "nav.shopping"),
                        Triple(Tab.Calendar, Icons.Default.CalendarMonth, "nav.calendar"),
                        Triple(Tab.More, Icons.Default.Menu, "common.more"),
                    ).forEach { (tab, icon, label) ->
                        NavigationBarItem(
                            selected = nav.tab == tab,
                            onClick = { nav.open(tab) },
                            icon = { Icon(icon, null) },
                            label = { Text(t(label), maxLines = 1, fontSize = 11.sp) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (nav.tab) {
                    Tab.Dashboard -> DashboardScreen()
                    Tab.Chores -> ChoresScreen()
                    Tab.Shopping -> ShoppingScreen()
                    Tab.Calendar -> CalendarScreen()
                    Tab.More -> when (nav.page) {
                        null -> MoreScreen()
                        Page.Notes -> NotesScreen()
                        Page.Expenses -> ExpensesScreen()
                        Page.Points -> PointsScreen()
                        Page.Dishwasher -> DishwasherScreen()
                        Page.Members -> MembersScreen()
                        Page.Settings -> SettingsScreen()
                        Page.Profile -> ProfileScreen()
                    }
                }
            }
        }
    }
}

private fun pageTitle(page: Page, t: de.wghub.app.i18n.Texts) = when (page) {
    Page.Notes -> t("nav.notes")
    Page.Expenses -> t("nav.expenses")
    Page.Points -> t("nav.points")
    Page.Dishwasher -> t("nav.dishwasher")
    Page.Members -> t("nav.members")
    Page.Settings -> t("nav.settings")
    Page.Profile -> t("nav.profile")
}

private val LANGUAGES = listOf("de" to "Deutsch", "en" to "English", "ru" to "Русский")

/** Mirrors frontend/src/views/MoreView.vue. */
@Composable
fun MoreScreen() {
    val app = LocalApp.current
    val t = LocalTexts.current
    val nav = LocalNav.current
    val household = LocalHousehold.current
    val scope = rememberCoroutineScope()
    val user = app.user
    val others = app.repo.cached("/households", HouseholdsResponse.serializer())?.households.orEmpty()
        .filter { it.id != household.id }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            val links = buildList {
                add(Triple(Page.Notes, Icons.AutoMirrored.Filled.StickyNote2, "nav.notes"))
                add(Triple(Page.Expenses, Icons.Default.Wallet, "nav.expenses"))
                add(Triple(Page.Points, Icons.Default.EmojiEvents, "nav.points"))
                // Not every WG has a dishwasher, so this appears once the admin turns it on.
                if (household.detail.household.dishwasherTarget != null) add(Triple(Page.Dishwasher, Icons.Default.Kitchen, "nav.dishwasher"))
                add(Triple(Page.Members, Icons.Default.Group, "nav.members"))
                add(Triple(Page.Settings, Icons.Default.Settings, "nav.settings"))
                add(Triple(Page.Profile, Icons.Default.AccountCircle, "nav.profile"))
            }
            links.forEachIndexed { index, (page, icon, label) ->
                if (index > 0) androidx.compose.material3.HorizontalDivider(color = Wg.colors.line)
                LinkRow(t(label), icon) { nav.open(page) }
            }
        }

        if (user != null) {
            Column {
                SectionTitle(t("profile.language"))
                Segmented(LANGUAGES, user.locale, Modifier.padding(top = 2.dp)) { locale ->
                    scope.launch { app.attempt { app.setLocale(locale) } }
                }
            }
        }

        Column {
            SectionTitle(t("nav.switchWg"))
            Card {
                others.forEach { other ->
                    LinkRow("${other.icon}  ${other.name}", null) { scope.launch { app.attempt { app.openHousehold(other.id) } } }
                    androidx.compose.material3.HorizontalDivider(color = Wg.colors.line)
                }
                LinkRow(t("nav.newWg"), Icons.Default.Add) { app.showWelcome() }
            }
        }

        Card { LinkRow(t("auth.logout"), Icons.AutoMirrored.Filled.Logout, danger = true) { app.signOut() } }

        Text(
            "${t("app.server")}: ${app.repo.settings.serverUrl}",
            fontSize = 12.sp,
            color = Wg.colors.muted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
