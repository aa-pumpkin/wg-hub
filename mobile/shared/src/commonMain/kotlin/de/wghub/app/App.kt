package de.wghub.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.wghub.app.data.Repository
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.ui.HouseholdPicker
import de.wghub.app.ui.LoginScreen
import de.wghub.app.ui.MainScreen
import de.wghub.app.ui.WgTheme
import kotlinx.coroutines.launch

val LocalApp = compositionLocalOf<AppState> { error("no app state") }

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val app = remember { AppState(Repository(), scope) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        app.onMessage = { text -> scope.launch { snackbar.showSnackbar(text) } }
        app.start()
    }

    WgTheme(app.theme) {
        val texts = app.texts
        if (texts == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@WgTheme
        }
        CompositionLocalProvider(LocalTexts provides texts, LocalApp provides app) {
            Box(Modifier.fillMaxSize()) {
                when (val screen = app.screen) {
                    Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    Screen.Login -> LoginScreen()
                    is Screen.PickHousehold -> HouseholdPicker(screen.households)
                    is Screen.Main -> MainScreen(screen.detail)
                }
                // Above the bottom tabs and the phone's own buttons.
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).safeDrawingPadding()
                        .padding(bottom = if (app.screen is Screen.Main) 80.dp else 0.dp),
                )
            }
        }
    }
}
