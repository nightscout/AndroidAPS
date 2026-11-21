package app.aaps.plugins.eversense.reference.util

/**
 * Silent stand-in for the frozen driver's own logger, which needed logback. The frozen sources in
 * `protocol-reference.zip` are compiled into the host tests only, to compare bytes and results with the live driver;
 * what they log does not matter there.
 */
object EversenseLogger {

    @Suppress("unused") fun configure(logDir: String) = Unit
    @Suppress("unused") fun setDiagnosticLogging(enabled: Boolean) = Unit
    @Suppress("unused") fun debug(tag: String, message: () -> String) = Unit
    @Suppress("unused") inline fun diagnostic(block: () -> Unit) = Unit
    @Suppress("unused") fun info(tag: String, message: String) = Unit
    @Suppress("unused") fun warning(tag: String, message: String) = Unit
    @Suppress("unused") fun error(tag: String, message: String) = Unit
}
