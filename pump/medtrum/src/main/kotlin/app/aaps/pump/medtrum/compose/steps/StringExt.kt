package app.aaps.pump.medtrum.compose.steps

internal fun String.stripHtml(): String = this.replace(Regex("<[^>]*>"), "")
