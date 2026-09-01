package app.aaps.implementations

import app.aaps.core.keys.interfaces.TextRef
import android.os.Build
import app.aaps.BuildConfig
import app.aaps.R
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.di.ExternalOptionsOverride
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.zacsweers.metro.Inject

// @Singleton (not @Reusable): Config owns the single app-global init-progress flow that
// ComposeMainActivity's splash gate observes; a guaranteed single instance keeps that flow shared
// (also required so an instrumented test can flip initCompleted() on the same instance the UI reads).
@Suppress("KotlinConstantConditions")
@ContributesBinding(AppScope::class, binding = binding<Config>())
@SingleIn(AppScope::class)
class ConfigImpl @Inject constructor(
    private val fileListProvider: () -> FileListProvider,
    private val externalOptionsOverride: ExternalOptionsOverride
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
    override val deviceModelForUpload = Build.MANUFACTURER + " " + Build.MODEL
    // The unit-test android.jar leaves these statics null, and this one is assigned straight to a
    // non-null String, so it would fail graph creation in every test. The line above survives it only
    // because string concatenation prints "null".
    override val deviceManufacturer: String = Build.MANUFACTURER ?: ""
    override val appName: TextRef = TextRef.AndroidRes(R.string.app_name)

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
    // The override is read first and never cached. The instrumented tests change which options they
    // want between tests, so a cached answer from the first one would stick for the whole run. The file
    // lookup below stays cached - that really is fixed for the life of the process.
    override fun isEnabled(option: ExternalOptions): Boolean =
        option in externalOptionsOverride.enabled() ||
            enabledOptionsCache.getOrPut(option) {
                fileListProvider().ensureExtraDirExists()?.findFile(option.filename) != null
            }
}