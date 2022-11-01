package info.nightscout.androidaps.utils.buildHelper

import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.interfaces.BuildHelper
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import java.io.File

class BuildHelperImpl constructor(
    private val config: Config,
    fileListProvider: PrefFileListProvider
) : BuildHelper {

    private var devBranch = false
    private var engineeringMode = false
    private var unfinishedMode = false

    init {
        val engineeringModeSemaphore = File(fileListProvider.ensureExtraDirExists(), "engineering_mode")
        val unfinishedModeSemaphore = File(fileListProvider.ensureExtraDirExists(), "unfinished_mode")

        engineeringMode = engineeringModeSemaphore.exists() && engineeringModeSemaphore.isFile
        unfinishedMode = unfinishedModeSemaphore.exists() && unfinishedModeSemaphore.isFile
        devBranch = BuildConfig.VERSION.contains("-") || BuildConfig.VERSION.matches(Regex(".*[a-zA-Z]+.*"))
    }

    override fun isEngineeringModeOrRelease(): Boolean =
        if (!config.APS) true else engineeringMode || !devBranch

    override fun isEngineeringMode(): Boolean = engineeringMode

    override fun isUnfinishedMode(): Boolean = unfinishedMode

    override fun isDev(): Boolean = devBranch
}