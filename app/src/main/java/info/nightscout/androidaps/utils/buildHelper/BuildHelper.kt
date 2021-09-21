package info.nightscout.androidaps.utils.buildHelper

import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class BuildHelper @Inject constructor(
    private val config: Config,
    fileListProvider: PrefFileListProvider
) {

    private var devBranch = false
    private var engineeringMode = false

    init {
        val engineeringModeSemaphore = File(fileListProvider.ensureExtraDirExists(), "engineering__mode")

        engineeringMode = engineeringModeSemaphore.exists() && engineeringModeSemaphore.isFile
        devBranch = BuildConfig.VERSION.contains("-") || BuildConfig.VERSION.matches(Regex(".*[a-zA-Z]+.*"))
    }

    fun isEngineeringModeOrRelease(): Boolean =
        if (!config.APS) true else engineeringMode || !devBranch

    fun isEngineeringMode(): Boolean =
        engineeringMode

    fun isDev(): Boolean = devBranch
}