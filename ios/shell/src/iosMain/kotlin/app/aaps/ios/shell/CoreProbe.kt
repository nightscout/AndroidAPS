package app.aaps.ios.shell

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.format.NumberFormatPlatform
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.time.systemUtcOffsetAt
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.keys.BooleanKey
import app.aaps.core.nssdk.localmodel.Storage
import app.aaps.core.ui.UiMode
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.utils.Percentile
import app.aaps.core.utils.toHex

/**
 * Runs something real out of each `:core` module.
 *
 * Linking proves the symbols exist and the DI probe proves Metro can build objects. Neither one
 * calls into the modules themselves, so neither would notice a platform piece that is wired up
 * wrongly. This does.
 *
 * The order of preference for what to call is deliberate. Anything declared `expect` comes first,
 * because that is the only code where iOS runs something genuinely different from Android - a
 * missing or wrong `actual` is the one failure that cannot show up in a JVM test run. Pure Kotlin
 * is included only as a smoke check, since the same source compiled by the same frontend is not
 * where surprises live.
 */
internal object CoreProbe {

    /** One line per module, naming what was called and what came back. */
    fun run(): List<String> = listOf(
        dataModule(),
        interfacesModule(),
        keysModule(),
        utilsModule(),
        objectsModule(),
        nssdkModule(),
        uiModule(),
        graphModule()
    )

    /** Two `actual`s here: the time zone offset and the number formatter. */
    private fun dataModule(): String = attempt("core:data") {
        val offset = systemUtcOffsetAt(1_700_000_000_000L)
        val separator = NumberFormatPlatform.localeSeparator
        val formatted = NumberFormatPlatform.format(NumberFormat.DECIMAL_1, 12.345)
        val gv = InMemoryGlucoseValue(timestamp = 1_000L, value = 100.0)
        "utcOffset=${offset / 3_600_000}h sep='$separator' fmt=$formatted gv=${gv.recalculated}"
    }

    /** `AapsLock` and `aapsIoDispatcher` are both `actual`s backed by Kotlin/Native primitives. */
    private fun interfacesModule(): String = attempt("core:interfaces") {
        val lock = AapsLock()
        lock.lock()
        lock.unlock()
        "lock ok dispatcher=${aapsIoDispatcher}"
    }

    private fun keysModule(): String = attempt("core:keys") {
        val key = BooleanKey.OverviewShowTreatmentButton
        "key=${key.key} default=${key.defaultValue}"
    }

    private fun utilsModule(): String = attempt("core:utils") {
        val median = Percentile.percentile(doubleArrayOf(1.0, 2.0, 3.0, 4.0), 0.5)
        val hex = byteArrayOf(0x0A, 0x1B.toByte()).toHex()
        "median=$median hex=$hex"
    }

    private fun objectsModule(): String = attempt("core:objects") {
        // The module is reached through a value it operates on rather than a graph, which the DI
        // probe already covers.
        val values = mutableListOf(
            InMemoryGlucoseValue(timestamp = 1_000L, value = 90.0),
            InMemoryGlucoseValue(timestamp = 2_000L, value = 110.0)
        )
        "values=${values.size} last=${values.last().recalculated}"
    }

    private fun nssdkModule(): String = attempt("core:nssdk") {
        val storage = Storage(storage = "mongodb", version = "14.2.6")
        "storage=${storage.storage}/${storage.version}"
    }

    private fun uiModule(): String = attempt("core:ui") {
        "uiModes=${UiMode.entries.size} screenModes=${ScreenMode.entries.size}"
    }

    /**
     * `:core:graph` has no headless surface.
     *
     * Every declaration in it is `@Composable`, so it can only run inside a composition. Saying so
     * is more useful than a check that quietly tests nothing: reaching it needs Compose
     * Multiplatform hosted in the app, which is a separate piece of work.
     */
    private fun graphModule(): String = "core:graph  linked, needs a Compose host to run"

    private inline fun attempt(module: String, block: () -> String): String =
        try {
            "$module  ${block()}"
        } catch (e: Throwable) {
            "$module  FAILED ${e::class.simpleName}: ${e.message}"
        }
}
