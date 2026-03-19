package app.aaps.implementations

import android.os.Build
import app.aaps.BuildConfig
import app.aaps.R
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.interfaces.maintenance.FileListProvider
import dagger.Lazy
import dagger.Reusable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@Suppress("KotlinConstantConditions")
@Reusable
class ConfigImpl @Inject constructor(
    private val fileListProvider: Lazy<FileListProvider>
) : Config {

    override val SUPPORTED_NS_VERSION = 150000 // 15.0.0
    override val APS = BuildConfig.FLAVOR == "full"
    override val AAPSCLIENT = BuildConfig.FLAVOR == "aapsclient" || BuildConfig.FLAVOR == "aapsclient2" || BuildConfig.FLAVOR == "aapsclient3"
    override val AAPSCLIENT1 = BuildConfig.FLAVOR == "aapsclient"
    override val AAPSCLIENT2 = BuildConfig.FLAVOR == "aapsclient2"
    override val AAPSCLIENT3 = BuildConfig.FLAVOR == "aapsclient3"
    override val PUMPCONTROL = BuildConfig.FLAVOR == "pumpcontrol"
    override val PUMPDRIVERS = BuildConfig.FLAVOR == "full" || BuildConfig.FLAVOR == "pumpcontrol"
    override val FLAVOR = BuildConfig.FLAVOR
    override val VERSION_NAME = BuildConfig.VERSION_NAME
    override val HEAD = BuildConfig.HEAD
    override val COMMITTED = BuildConfig.COMMITTED.toBoolean()
    override val BUILD_VERSION = BuildConfig.BUILDVERSION
    override val REMOTE: String = BuildConfig.REMOTE
    override val BUILD_TYPE: String = BuildConfig.BUILD_TYPE
    override val VERSION: String = BuildConfig.VERSION
    override val APPLICATION_ID: String = BuildConfig.APPLICATION_ID
    override val DEBUG = BuildConfig.DEBUG

    override val currentDeviceModelString = Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")"
    override val appName: Int = R.string.app_name

    private val _initProgressFlow = MutableStateFlow(InitProgress())
    override val initProgressFlow: StateFlow<InitProgress> = _initProgressFlow.asStateFlow()
    override fun updateInitProgress(step: String, current: Int, total: Int) {
        _initProgressFlow.value = _initProgressFlow.value.copy(step = step, current = current, total = total)
    }

    override fun initCompleted() {
        _initProgressFlow.value = _initProgressFlow.value.copy(done = true)
    }

    override fun initFailed(error: String) {
        _initProgressFlow.value = _initProgressFlow.value.copy(error = error)
    }

    private val _initSnackbarFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val initSnackbarFlow: SharedFlow<String> = _initSnackbarFlow.asSharedFlow()
    override fun showInitSnackbar(message: String) {
        _initSnackbarFlow.tryEmit(message)
    }

    private var isEngineeringMode: Boolean? = null
    private var isUnfinishedMode: Boolean? = null
    private var showUserActionsOnWatchOnly: Boolean? = null
    private var ignoreNightscoutV3Errors: Boolean? = null
    private var doNotSendSmsOnProfileChange: Boolean? = null
    private var enableAutotune: Boolean? = null
    private var disableLeakCanary: Boolean? = null
    private var emulateDanaRSv1: Boolean? = null
    private var emulateDanaRSv3: Boolean? = null
    private var emulateDanaBLE5: Boolean? = null

    override fun isEngineeringModeOrRelease(): Boolean = if (!APS) true else isEngineeringMode() || !isDev()
    override fun isEngineeringMode(): Boolean = isEngineeringMode ?: (fileListProvider.get().ensureExtraDirExists()?.findFile("engineering_mode") != null).also { isEngineeringMode = it }
    override fun isUnfinishedMode(): Boolean = isUnfinishedMode ?: (fileListProvider.get().ensureExtraDirExists()?.findFile("unfinished_mode") != null).also { isUnfinishedMode = it }
    override fun isDev(): Boolean = (VERSION.contains("-") || VERSION.matches(Regex(".*[a-zA-Z]+.*"))) && !VERSION.contains("-beta") && !VERSION.contains("-rc")
    override fun showUserActionsOnWatchOnly(): Boolean = showUserActionsOnWatchOnly ?: (fileListProvider.get().ensureExtraDirExists()?.findFile("show_user_actions_on_watch_only") != null).also { showUserActionsOnWatchOnly = it }
    override fun ignoreNightscoutV3Errors(): Boolean = ignoreNightscoutV3Errors ?: (fileListProvider.get().ensureExtraDirExists()?.findFile("ignore_nightscout_v3_errors") != null).also { ignoreNightscoutV3Errors = it }
    override fun doNotSendSmsOnProfileChange(): Boolean = doNotSendSmsOnProfileChange ?: (fileListProvider.get().ensureExtraDirExists()?.findFile("do_not_send_sms_on_profile_change") != null).also { doNotSendSmsOnProfileChange = it }
    override fun enableAutotune(): Boolean = enableAutotune ?: (fileListProvider.get().ensureExtraDirExists()?.findFile("enable_autotune") != null).also { enableAutotune = it }
    override fun disableLeakCanary(): Boolean = disableLeakCanary ?: (fileListProvider.get().ensureExtraDirExists()?.findFile("disable_leakcanary") != null).also { disableLeakCanary = it }
    override fun emulateDanaRSv1(): Boolean = emulateDanaRSv1 ?: (fileListProvider.get().ensureExtraDirExists()?.findFile("emulate_dana_rs_v1") != null).also { emulateDanaRSv1 = it }
    override fun emulateDanaRSv3(): Boolean = emulateDanaRSv3 ?: (fileListProvider.get().ensureExtraDirExists()?.findFile("emulate_dana_rs_v3") != null).also { emulateDanaRSv3 = it }
    override fun emulateDanaBLE5(): Boolean = emulateDanaBLE5 ?: (fileListProvider.get().ensureExtraDirExists()?.findFile("emulate_dana_ble5") != null).also { emulateDanaBLE5 = it }
}