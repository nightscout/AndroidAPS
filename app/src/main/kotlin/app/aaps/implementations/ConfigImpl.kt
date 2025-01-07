package app.aaps.implementations

import android.os.Build
import app.aaps.BuildConfig
import app.aaps.R
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.maintenance.FileListProvider
import dagger.Reusable
import javax.inject.Inject

@Suppress("KotlinConstantConditions")
@Reusable
class ConfigImpl @Inject constructor(
    private val fileListProvider: FileListProvider
) : Config {

    override val SUPPORTED_NS_VERSION = 150000 // 15.0.0
    override val APS = BuildConfig.FLAVOR == "full"
    override val AAPSCLIENT = BuildConfig.FLAVOR == "aapsclient" || BuildConfig.FLAVOR == "aapsclient2"
    override val AAPSCLIENT1 = BuildConfig.FLAVOR == "aapsclient"
    override val AAPSCLIENT2 = BuildConfig.FLAVOR == "aapsclient2"
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

    override val currentDeviceModelString = Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")"
    override val appName: Int = R.string.app_name

    override var appInitialized: Boolean = false

    private var isEngineeringMode: Boolean? = null
    private var isUnfinishedMode: Boolean? = null
    private var showUserActionsOnWatchOnly: Boolean? = null
    private var ignoreNightscoutV3Errors: Boolean? = null
    private var doNotSendSmsOnProfileChange: Boolean? = null

    override fun isEngineeringModeOrRelease(): Boolean = if (!APS) true else isEngineeringMode() || !isDev()
    override fun isEngineeringMode(): Boolean = isEngineeringMode ?: (fileListProvider.ensureExtraDirExists()?.findFile("engineering_mode") != null).also { isEngineeringMode = it }
    override fun isUnfinishedMode(): Boolean = isUnfinishedMode ?: (fileListProvider.ensureExtraDirExists()?.findFile("unfinished_mode") != null).also { isUnfinishedMode = it }
    override fun isDev(): Boolean = (VERSION.contains("-") || VERSION.matches(Regex(".*[a-zA-Z]+.*"))) && !VERSION.contains("-beta") && !VERSION.contains("-rc")
    override fun showUserActionsOnWatchOnly(): Boolean = showUserActionsOnWatchOnly ?: (fileListProvider.ensureExtraDirExists()?.findFile("show_user_actions_on_watch_only") != null).also { showUserActionsOnWatchOnly = it }
    override fun ignoreNightscoutV3Errors(): Boolean = ignoreNightscoutV3Errors ?: (fileListProvider.ensureExtraDirExists()?.findFile("ignore_nightscout_v3_errors") != null).also { ignoreNightscoutV3Errors = it }
    override fun doNotSendSmsOnProfileChange(): Boolean = doNotSendSmsOnProfileChange ?: (fileListProvider.ensureExtraDirExists()?.findFile("do_not_send_sms_on_profile_change") != null).also { doNotSendSmsOnProfileChange = it }
}