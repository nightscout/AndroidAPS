package info.nightscout.androidaps.utils.buildHelper

import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.plugins.general.maintenance.LoggerUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildHelper @Inject constructor(private val config: Config) {

    private var devBranch = false
    private var engineeringMode = false

    init {
        val extFilesDir = LoggerUtils.getLogDirectory()
        val engineeringModeSemaphore = File(extFilesDir, "engineering__mode")

        engineeringMode = engineeringModeSemaphore.exists() && engineeringModeSemaphore.isFile
        devBranch = BuildConfig.VERSION.contains("-") || BuildConfig.VERSION.matches(Regex(".*[a-zA-Z]+.*"))
    }

    fun isEngineeringModeOrRelease(): Boolean =
        if (!config.APS) true else engineeringMode || !devBranch

    fun isEngineeringMode(): Boolean =
        engineeringMode

    fun isDev(): Boolean = devBranch
}