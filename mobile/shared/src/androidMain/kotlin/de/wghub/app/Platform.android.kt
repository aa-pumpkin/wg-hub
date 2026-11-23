package de.wghub.app

import java.util.Locale

actual fun deviceLanguage(): String = Locale.getDefault().language
