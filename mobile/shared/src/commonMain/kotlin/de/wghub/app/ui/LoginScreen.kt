package de.wghub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.HouseholdListItem
import de.wghub.app.i18n.LocalTexts
import kotlinx.coroutines.launch

@Composable
private fun AuthPage(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Wg.colors.bg).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            content()
        }
    }
}

/** Login and registration (frontend/src/views/LoginView.vue, RegisterView.vue). */
@Composable
fun LoginScreen() {
    val app = LocalApp.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    var registering by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(app.repo.settings.serverUrl) }
    var showServer by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        busy = true
        error = null
        errors = emptyMap()
        scope.launch {
            try {
                if (registering) app.register(server, displayName, username, password)
                else app.login(server, username, password)
            } catch (e: de.wghub.app.data.ApiException) {
                if (e.fields.isNotEmpty()) errors = e.fields else error = app.errorText(e)
            } finally {
                busy = false
            }
        }
    }

    AuthPage {
        Text(
            "WG Hub", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Wg.colors.accentText,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp),
        )
        Card {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t(if (registering) "auth.registerTitle" else "auth.loginTitle"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(t(if (registering) "auth.registerSubtitle" else "auth.loginSubtitle"), fontSize = 14.sp, color = Wg.colors.muted)
                if (registering) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(t("auth.displayName")) },
                        isError = "displayName" in errors,
                        supportingText = { Text(t.fieldError(errors["displayName"]) ?: t("auth.displayNameHint")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(t("auth.username")) },
                    isError = "username" in errors,
                    supportingText = if (registering) ({ Text(t.fieldError(errors["username"]) ?: t("auth.usernameHint")) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(t("auth.password")) },
                    isError = "password" in errors,
                    supportingText = if (registering) ({ Text(t.fieldError(errors["password"]) ?: t("auth.passwordHint")) }) else null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) Text(error!!, color = Wg.colors.danger, fontSize = 14.sp)
                Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(t(if (registering) "auth.register" else "auth.login"))
                }
                Hint(t(if (registering) "auth.anyDeviceHint" else "auth.forgotHint"))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t(if (registering) "auth.hasAccount" else "auth.noAccount"), fontSize = 14.sp, color = Wg.colors.muted)
            Text(
                t(if (registering) "auth.login" else "auth.register"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Wg.colors.accentText,
                modifier = Modifier.clickable {
                    registering = !registering
                    errors = emptyMap()
                    error = null
                }.padding(8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Rarely needed, so tucked away: only when the PC's address changes.
        Text(
            "${t("app.server")}: $server",
            fontSize = 13.sp,
            color = Wg.colors.muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable { showServer = !showServer }.padding(8.dp),
        )
        if (showServer) {
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(t("app.server")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Your WGs, plus creating or joining one (frontend/src/views/WelcomeView.vue). */
@Composable
fun HouseholdPicker(households: List<HouseholdListItem>) {
    val app = LocalApp.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var createErrors by remember { mutableStateOf(emptyMap<String, String>()) }
    var codeError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    BackHandler(enabled = app.repo.settings.lastHouseholdId != null) {
        scope.launch { app.attempt { app.openHousehold(app.repo.settings.lastHouseholdId!!) } }
    }

    AuthPage {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t("welcome.title", "name" to (app.user?.displayName ?: "")), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Hint(t("welcome.subtitle"))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (households.isNotEmpty()) {
                CardList(households) { item ->
                    ListRow(Modifier.clickable { scope.launch { app.attempt { app.openHousehold(item.id) } } }) {
                        Text(item.icon, fontSize = 22.sp)
                        Column(Modifier.weight(1f)) {
                            RowTitle(item.name)
                            RowMeta(t("invite.members", "count" to item.memberCount))
                        }
                    }
                }
            }
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t("welcome.createTitle"), fontWeight = FontWeight.Medium)
                    Hint(t("welcome.createText"))
                    Field(name, { name = it }, t("welcome.wgName"), t.fieldError(createErrors["name"]), t("welcome.wgNamePlaceholder"))
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                createErrors = app.submit { app.createHousehold(name) }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(t("common.create")) }
                }
            }
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t("welcome.joinTitle"), fontWeight = FontWeight.Medium)
                    Hint(t("welcome.joinText"))
                    Field(code, { code = it }, t("welcome.code"), codeError)
                    OutlinedButton(
                        onClick = {
                            if (code.isBlank()) {
                                codeError = t("welcome.invalidCode")
                                return@OutlinedButton
                            }
                            codeError = null
                            busy = true
                            scope.launch {
                                app.attempt { app.joinHousehold(code) }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(t("welcome.join")) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(t("auth.logout"), color = Wg.colors.muted, modifier = Modifier.clickable { app.signOut() }.padding(12.dp))
            }
        }
    }
}
