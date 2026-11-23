package de.wghub.app.ui

import androidx.compose.runtime.Composable

/** The phone's back gesture or button. */
@Composable
expect fun BackHandler(enabled: Boolean, onBack: () -> Unit)
