package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.autotune.AutotuneFileStore
import app.aaps.core.interfaces.logging.LoggerUtils
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** The Autotune folders in the log folder of the app, where Autotune has always put them on Android. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AutotuneFileStore>())
@Inject
class AndroidAutotuneFileStore(
    private val loggerUtils: LoggerUtils
) : JvmAutotuneFileStore() {

    override val directory: String get() = loggerUtils.logDirectory
}
