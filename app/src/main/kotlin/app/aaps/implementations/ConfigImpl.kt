package app.aaps.implementations

import android.os.Build
import app.aaps.BuildConfig
import app.aaps.R
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
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

    private val enabledOptionsCache = mutableMapOf<ExternalOptions, Boolean>()

    override fun isEngineeringModeOrRelease(): Boolean = if (!APS) true else isEngineeringMode() || !isDev()
    override fun isEngineeringMode(): Boolean = isEnabled(ExternalOptions.ENGINEERING_MODE)
    override fun isDev(): Boolean = (VERSION.contains("-") || VERSION.matches(Regex(".*[a-zA-Z]+.*"))) && !VERSION.contains("-beta") && !VERSION.contains("-rc")
    override fun isEnabled(option: ExternalOptions): Boolean =
        enabledOptionsCache.getOrPut(option) {
            fileListProvider.get().ensureExtraDirExists()?.findFile(option.filename) != null
        }
}