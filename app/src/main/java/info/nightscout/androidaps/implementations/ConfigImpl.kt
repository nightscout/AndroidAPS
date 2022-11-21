package info.nightscout.androidaps.implementations

import android.os.Build
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import info.nightscout.interfaces.Config
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigImpl @Inject constructor(
    fileListProvider: PrefFileListProvider
) : Config {

    override val SUPPORTEDNSVERSION = 140206 // 14.2.6
    override val APS = BuildConfig.FLAVOR == "full"
    override val NSCLIENT = BuildConfig.FLAVOR == "aapsclient" || BuildConfig.FLAVOR == "aapsclient2"
    override val PUMPCONTROL = BuildConfig.FLAVOR == "pumpcontrol"
    override val PUMPDRIVERS = BuildConfig.FLAVOR == "full" || BuildConfig.FLAVOR == "pumpcontrol"
    override val FLAVOR = BuildConfig.FLAVOR
    override val VERSION_NAME = BuildConfig.VERSION_NAME
    override val BUILD_VERSION = BuildConfig.BUILDVERSION
    override val DEBUG = BuildConfig.DEBUG

    override val currentDeviceModelString =
        Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")"
    override val appName: Int = R.string.app_name

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
        if (!APS) true else engineeringMode || !devBranch

    override fun isEngineeringMode(): Boolean = engineeringMode

    override fun isUnfinishedMode(): Boolean = unfinishedMode

    override fun isDev(): Boolean = devBranch
}