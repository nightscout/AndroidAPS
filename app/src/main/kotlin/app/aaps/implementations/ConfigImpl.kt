package app.aaps.implementations

import android.os.Build
import app.aaps.BuildConfig
import app.aaps.R
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.maintenance.PrefFileListProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("KotlinConstantConditions")
@Singleton
class ConfigImpl @Inject constructor(
    fileListProvider: PrefFileListProvider
) : Config {

    override val SUPPORTED_NS_VERSION = 150000 // 15.0.0
    override val APS = BuildConfig.FLAVOR == "full"
    override val NSCLIENT = BuildConfig.FLAVOR == "aapsclient" || BuildConfig.FLAVOR == "aapsclient2"
    override val NSCLIENT1 = BuildConfig.FLAVOR == "aapsclient"
    override val NSCLIENT2 = BuildConfig.FLAVOR == "aapsclient2"
    override val PUMPCONTROL = BuildConfig.FLAVOR == "pumpcontrol"
    override val PUMPDRIVERS = BuildConfig.FLAVOR == "full" || BuildConfig.FLAVOR == "pumpcontrol"
    override val FLAVOR = BuildConfig.FLAVOR
    override val VERSION_NAME = BuildConfig.VERSION_NAME
    override val BUILD_VERSION = BuildConfig.BUILDVERSION
    override val REMOTE: String = BuildConfig.REMOTE
    override val BUILD_TYPE: String = BuildConfig.BUILD_TYPE
    override val VERSION: String = BuildConfig.VERSION
    override val APPLICATION_ID: String = BuildConfig.APPLICATION_ID
    override val DEBUG = BuildConfig.DEBUG

    override val currentDeviceModelString =
        Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")"
    override val appName: Int = R.string.app_name

    override var appInitialized: Boolean = false

    private var devBranch = false
    private var engineeringMode = false
    private var unfinishedMode = false

    init {
        val engineeringModeSemaphore = File(fileListProvider.ensureExtraDirExists(), "engineering_mode")
        val unfinishedModeSemaphore = File(fileListProvider.ensureExtraDirExists(), "unfinished_mode")

        engineeringMode = engineeringModeSemaphore.exists() && engineeringModeSemaphore.isFile
        unfinishedMode = unfinishedModeSemaphore.exists() && unfinishedModeSemaphore.isFile
        devBranch = VERSION.contains("-") || VERSION.matches(Regex(".*[a-zA-Z]+.*"))
        if (VERSION.contains("-beta") || VERSION.contains("-rc"))
            devBranch = false
    }

    override fun isEngineeringModeOrRelease(): Boolean =
        if (!APS) true else engineeringMode || !devBranch

    override fun isEngineeringMode(): Boolean = engineeringMode

    override fun isUnfinishedMode(): Boolean = unfinishedMode

    override fun isDev(): Boolean = devBranch
}