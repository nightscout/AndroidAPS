package app.aaps

import android.app.Application
import android.app.LocaleManager
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.LocaleList
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import app.aaps.appshell.start.AppStartPlatform
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.pump.emulator.PumpEmulators
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.MetroViewModelFactoryOwner
import app.aaps.core.utils.gitRemoteShort
import app.aaps.database.di.DatabaseConfig
import app.aaps.di.ExternalOptionsOverride
import app.aaps.di.GeneratedStringOwners
import app.aaps.di.metro.MetroGraphs
import app.aaps.di.metro.MetroWorkerFactory
import app.aaps.implementation.receivers.BTReceiver
import app.aaps.implementation.receivers.ChargingStateReceiver
import app.aaps.implementation.receivers.KeepAliveWorker
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.implementation.receivers.TimeDateOrTZChangeReceiver
import app.aaps.settingsstore.AndroidStorage
import app.aaps.settingsstore.IdleWorkerFactory
import app.aaps.settingsstore.SettingsUnreadableNotification
import app.aaps.utils.configureLeakCanary
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.remoteConfig
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import rxdogtag2.RxDogTag
import java.io.IOException
import java.util.Locale
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.time.Duration.Companion.milliseconds
import android.content.res.Configuration as ResourcesConfiguration
import app.aaps.core.ui.R as CoreUiR

class MainApp : Application(), MetroMemberInjector, MetroViewModelFactoryOwner, Configuration.Provider {

    companion object {

    }

    // When the settings cannot be opened, ComposeMainActivity only hands over to SettingsUnreadableActivity and
    // finishes. Filling its fields would build some 40 objects - plugins, the command queue - for nothing,
    // and on settings that are not the user's.
    override fun injectMembers(target: Any): Boolean =
        if (AndroidStorage.failure != null && target is ComposeMainActivity) true
        else metroGraphs.injectMembers(target)

    override val metroViewModelFactory: MetroViewModelFactory get() = metroGraphs.viewModelFactory

    /**
     * `by lazy` so nothing is resolved before `onCreate` runs - the graph reaches Android services, and
     * an Application field initialiser runs before the framework is ready for that.
     * The last three arguments are the only things an instrumented test does differently; see
     * `AppRootGraph.Factory`. Production wants the real database, no forced options and new emulated pumps.
     */
    private val metroGraphs by lazy {
        MetroGraphs(
            context = this,
            memberInjector = this,
            databaseConfig = DatabaseConfig.PRODUCTION,
            externalOptionsOverride = ExternalOptionsOverride.NONE,
            pumpEmulators = PumpEmulators()
        )
    }
    // WorkManager on-demand initialization. `MetroWorkerFactory` builds every worker in the app; what
    // it does not know returns null and falls back to WorkManager's default reflective factory, which
    // is what builds WorkManager's own internal workers. The default androidx.startup
    // WorkManagerInitializer is removed in AndroidManifest.xml so this config wins.
    // While the settings cannot be opened, the app's jobs do nothing (see IdleWorkerFactory).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                if (AndroidStorage.failure == null) MetroWorkerFactory(metroGraphs)
                else IdleWorkerFactory { metroGraphs.workerCreators().keys }
            )
            .build()

    private val aapsLogger get() = metroGraphs.aapsLogger
    private val versionCheckersUtils get() = metroGraphs.versionCheckerUtils
    private val sp get() = metroGraphs.keyValueStore
    private val preferences get() = metroGraphs.preferences
    private val config get() = metroGraphs.config
    private val plugins get() = metroGraphs.allPlugins(aapsLogger)
    private val uiInteraction get() = metroGraphs.uiInteraction
    private val processLifecycleListener get() = metroGraphs.processLifecycleListener
    private val notificationManager get() = metroGraphs.notificationManager
    private val fabricPrivacy get() = metroGraphs.fabricPrivacy
    // The concrete types, only so their start() can be called below. Both are @Singleton, so these are
    // the same objects the interface bindings hand out.
    private val resourceHelperImpl get() = metroGraphs.resourceHelperImpl
    private val fabricPrivacyImpl get() = metroGraphs.fabricPrivacyImpl
    private val activePlugin get() = metroGraphs.activePlugin
    private val constraintChecker get() = metroGraphs.constraintsChecker
    private val signatureVerifierPlugin get() = metroGraphs.signatureVerifier
    private val fileListProvider get() = metroGraphs.fileListProvider
    private val cryptoUtil get() = metroGraphs.cryptoUtil
    private val exportPasswordDataStore get() = metroGraphs.exportPasswordDataStore
    private val widgetUpdater get() = metroGraphs.widgetUpdater
    private val appStartSequence get() = metroGraphs.appStartSequence
    private val appScope get() = metroGraphs.applicationScope
    private val uiRestart get() = metroGraphs.uiRestart

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshWidget: Runnable
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // The settings first: everything below reads them. Opened here on purpose, not on first use, so a
        // store that cannot be read is found at once and handled in one place.
        val storage = AndroidStorage.open(this)
        storage.failure?.let { failure ->
            startWithoutSettings(failure, storage.notes)
            return
        }
        GeneratedStringOwners.registerAll()
        resourceHelperImpl.start()
        // As early as possible, so the first screen is already in it
        applyAppLanguage()
        appLocales = resources.configuration.locales
        // Applies the analytics opt-out. Must come before configureLeakCanary below, which reports
        // through fabricPrivacy.
        fabricPrivacyImpl.start()
        // Build identity goes on the crash report here, not in setUserStats() near the end of the start.
        // A crash during plugin initialization happens seconds before that runs, so those reports carried
        // no HEAD and no Committed - exactly the ones where the build has to be known to tell a stale
        // local build from a live bug. Collection is already gated by the call above, so an opted-out
        // user still uploads nothing.
        setBuildIdentityKeys()

        // Here should be everything injected
        aapsLogger.debug("onCreate")
        storage.notes.forEach { aapsLogger.info(LTag.CORE, it) }
        AndroidStorage.onSaveFailure = { aapsLogger.error(LTag.CORE, "Settings could not be saved, the next change tries again: ${it.message}") }
        // Left by an earlier start whose settings could not be opened.
        SettingsUnreadableNotification.cancel(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleListener)

        // All initialization in background thread to avoid blocking main thread
        // (ComposeMainActivity shows splash screen until initProgress.done)
        scope.launch {
            try {
                // Configure LeakCanary with Firebase reporting. Memory leaks are uploaded to Firebase
                // Crashlytics via FabricPrivacy.logException. Not on the main thread: reading the
                // DISABLE_LEAK_CANARY marker lists the AAPS directory through SAF, and on slow phones
                // that held up app start long enough for an ANR (4.0.0-rc1).
                configureLeakCanary(
                    isEnabled = { !config.isEnabled(ExternalOptions.DISABLE_LEAK_CANARY) },
                    fabricPrivacy = fabricPrivacy
                )
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "LeakCanary could not be configured", e)
            }
            // The migrations, the database vacuum, the plugins and every start after them. The same
            // sequence runs on iOS and the desktop; what only Android does is in startPlatform.
            appStartSequence.run(plugins, startPlatform)
        }
    }

    /** What only Android does during the start. [AppStartSequence][app.aaps.appshell.start.AppStartSequence] decides when. */
    private val startPlatform = object : AppStartPlatform {

        override val versionCode: Int = BuildConfig.VERSION_CODE

        override val deviceName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"

        override fun startServices() {
            RxDogTag.install()
            setRxErrorHandler()
            followLanguage()

            aapsLogger.debug("Version: " + config.VERSION_NAME)
            aapsLogger.debug("BuildVersion: " + config.BUILD_VERSION)
            aapsLogger.debug("Remote: " + config.REMOTE)
            aapsLogger.debug("Phone: " + Build.MANUFACTURER + " " + Build.MODEL)
            registerLocalBroadcastReceiver()
            setupRemoteConfig()

            // trigger here to see the new version on app start after an update
            handler.postDelayed({ versionCheckersUtils.triggerCheckVersion() }, 30000)
        }

        override fun startKeepAlive() {
            KeepAliveWorker.schedule(this@MainApp)
        }

        override suspend fun beforeReady() {
            postFolderNotifications()

            //  schedule widget update
            refreshWidget = Runnable {
                handler.postDelayed(refreshWidget, 60000)
                widgetUpdater.update("ScheduleEveryMin")
            }
            handler.postDelayed(refreshWidget, 5000)
            setUserStats()
            passwordResetCheck()
            exportPasswordResetCheck()
        }
    }

    /**
     * The settings could not be opened ([AndroidStorage] says why), so AAPS does not start: no plugins, so no
     * loop, and nothing written to the database. The app runs on an empty store that lives in memory only,
     * which keeps its parts from crashing without writing over the unreadable file. A notification that
     * cannot be swiped away says so, and SettingsUnreadableActivity shows the way out.
     */
    private fun startWithoutSettings(failure: Exception, notes: List<String>) {
        GeneratedStringOwners.registerAll()
        // The user's answer about analytics and crash reports is in the settings that cannot be read, so it
        // is "no" now - in memory only, like everything else.
        sp.putBoolean(BooleanKey.MaintenanceEnableFabric.key, false)
        fabricPrivacyImpl.start()
        notes.forEach { aapsLogger.info(LTag.CORE, it) }
        aapsLogger.error(LTag.CORE, "The settings cannot be opened, so AAPS does not start", failure)
        SettingsUnreadableNotification.show(this, metroGraphs.iconsProvider.getNotificationIcon())
    }

    /**
     * Gives Android the language picked in AAPS as this app's language (Android 13 and newer). Android then shows
     * the whole app in it - every screen, the notifications, the widget - and keeps the default locale on it, so
     * numbers and dates are formatted in it too.
     *
     * AAPS used to move the default locale itself. Android sets the default locale back from the app's resources
     * on many configuration updates, often without calling [onConfigurationChanged], so a Czech AAPS on an English
     * phone showed "4.5" instead of "4,5" after its alarm screen had been shown.
     *
     * Android 12 has no app languages: there AAPS follows the phone's language, and the setting is hidden. Simple
     * mode follows the phone too. Nothing is sent when Android has this language already.
     */
    private fun applyAppLanguage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val tag = appLanguageTag(preferences.get(StringKey.GeneralLanguage), preferences.get(BooleanKey.GeneralSimpleMode))
        val wanted = tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        val localeManager = getSystemService(LocaleManager::class.java)
        if (localeManager.applicationLocales == wanted) return
        aapsLogger.debug(LTag.CORE, "App language: ${tag ?: "the phone's"}")
        localeManager.applicationLocales = wanted
    }

    /** A change of the AAPS language or of Simple mode goes to Android. What follows is in [onConfigurationChanged]. */
    private fun followLanguage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        appScope.launch {
            merge(preferences.observe(StringKey.GeneralLanguage), preferences.observe(BooleanKey.GeneralSimpleMode))
                .map { appLanguageTag(preferences.get(StringKey.GeneralLanguage), preferences.get(BooleanKey.GeneralSimpleMode)) }
                .distinctUntilChanged()
                .drop(1)
                .collect { applyAppLanguage() }
        }
    }

    /** The app's language as Android applies it, to notice a change. Null until the app has started. */
    private var appLocales: LocaleList? = null

    /**
     * The app's language changed: the one picked in AAPS ([applyAppLanguage]), or the phone's while AAPS follows it.
     * Android recreates the screens itself, but text built before - in the overview cache and in the view models -
     * is in the old language. So the screens are rebuilt through `UiRestart`, as iOS and the desktop do, and the home
     * screen widget draws again; it would otherwise wait up to a minute.
     *
     * Here and not where the language is picked: Android delivers the new language a moment later, and text read
     * before that would still come out in the old one.
     */
    override fun onConfigurationChanged(newConfig: ResourcesConfiguration) {
        super.onConfigurationChanged(newConfig)
        val previous = appLocales ?: return
        if (newConfig.locales == previous) return
        appLocales = newConfig.locales
        aapsLogger.debug(LTag.CORE, "App language changed to ${newConfig.locales.toLanguageTags()}")
        uiRestart.request()
        widgetUpdater.update("LanguageChanged")
    }

    /** The build this is, in short form - "github:owner/repo". Shared with iOS, which sends the same key. */
    private val remoteShort: String
        get() = gitRemoteShort(config.REMOTE)

    /**
     * Which build is running. All of it is known from BuildConfig before anything starts, so it is set as
     * early as collection is allowed - see the call in [onCreate].
     */
    private fun setBuildIdentityKeys() {
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("HEAD", BuildConfig.HEAD)
            setCustomKey("Version", config.VERSION_NAME)
            setCustomKey("BuildType", config.BUILD_TYPE)
            setCustomKey("BuildFlavor", config.FLAVOR)
            setCustomKey("Remote", remoteShort)
            setCustomKey("Committed", config.COMMITTED)
        }
    }

    private suspend fun setUserStats() {
        if (!fabricPrivacy.fabricEnabled()) return
        val closedLoopEnabled = if (constraintChecker.isClosedLoopAllowed().value()) "CLOSED_LOOP_ENABLED" else "CLOSED_LOOP_DISABLED"
        fabricPrivacy.setUserProperty("Mode", config.APPLICATION_ID + "-" + closedLoopEnabled)
        fabricPrivacy.setUserProperty("Language", preferences.getIfExists(StringKey.GeneralLanguage) ?: Locale.getDefault().language)
        fabricPrivacy.setUserProperty("Version", config.VERSION_NAME)
        fabricPrivacy.setUserProperty("HEAD", BuildConfig.BUILDVERSION)
        fabricPrivacy.setUserProperty("Remote", remoteShort)
        val hashes: List<String> = signatureVerifierPlugin.shortHashes()
        if (hashes.isNotEmpty()) fabricPrivacy.setUserProperty("Hash", hashes[0])
        activePlugin.activePumpInternal.let { fabricPrivacy.setUserProperty("Pump", it::class.java.simpleName) }
        if (!config.AAPSCLIENT && !config.PUMPCONTROL)
            activePlugin.activeAPS?.let { fabricPrivacy.setUserProperty("Aps", it::class.java.simpleName) }
        activePlugin.activeBgSource.let { fabricPrivacy.setUserProperty("BgSource", it::class.java.simpleName) }
        activePlugin.activeSensitivity.let { fabricPrivacy.setUserProperty("Sensitivity", it::class.java.simpleName) }
        // HEAD/Version/BuildType/BuildFlavor/Remote/Committed are set in setBuildIdentityKeys() during
        // onCreate. These two are not known that early.
        if (hashes.isNotEmpty()) FirebaseCrashlytics.getInstance().setCustomKey("Hash", hashes[0])
        FirebaseCrashlytics.getInstance().setCustomKey("Email", preferences.get(StringKey.MaintenanceIdentification))
    }

    private suspend fun passwordResetCheck() {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("PasswordReset")
        if (fh?.exists() == true) {
            config.updateInitProgress(getString(CoreUiR.string.waiting_for_pump))
            val serialNumber = withTimeoutOrNull(30_000.milliseconds) {
                while (activePlugin.activePump.serialNumber().isEmpty()) {
                    delay(100.milliseconds)
                }
                activePlugin.activePump.serialNumber()
            }
            if (serialNumber != null) {
                preferences.put(StringKey.ProtectionMasterPassword, cryptoUtil.hashPassword(serialNumber))
                fh.delete()
                exportPasswordDataStore.clearPasswordDataStore()
                config.showInitSnackbar(getString(CoreUiR.string.password_set))
            } else {
                aapsLogger.warn(LTag.CORE, "Password reset timed out waiting for pump serial number")
            }
        }
    }

    private fun exportPasswordResetCheck() {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("ExportPasswordReset")
        if (fh?.exists() == true) {
            exportPasswordDataStore.clearPasswordDataStore()
            fh.delete()
            config.showInitSnackbar(getString(CoreUiR.string.datastore_password_cleared))
        }
    }

    /** The AAPS folder is Android's own storage. The notices every platform shows are in the shared start. */
    private fun postFolderNotifications() {
        // AAPS directory not selected
        if (preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty())
            notificationManager.post(
                id = NotificationId.AAPS_DIR_NOT_SELECTED,
                TextRef.AndroidRes(CoreUiR.string.aaps_directory_not_selected),
                level = NotificationLevel.LOW,
                actions = listOf(NotificationAction(TextRef.AndroidRes(R.string.select)) {}),
                validityCheck = { preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty() }
            )
        // AAPS directory selected, but the permission behind it is gone.
        //
        // The check above only ever looked at whether the URI string is there, so this case was
        // completely silent: the directory is still "selected", the folder and the old exports are
        // still on the phone, and the Local export button simply goes grey. Android drops a persisted
        // SAF grant on reinstall and on "clear storage", so it happens to people who did nothing
        // wrong. Found on a real phone where the last local backup was four months old and nobody
        // knew - and a user with no cloud set up has, at that point, no backup at all.
        else if (!fileListProvider.isDirectoryAccessGranted())
            notificationManager.post(
                id = NotificationId.AAPS_DIR_ACCESS_LOST,
                TextRef.AndroidRes(CoreUiR.string.aaps_directory_access_lost),
                actions = listOf(NotificationAction(TextRef.AndroidRes(R.string.select)) {}),
                validityCheck = { !fileListProvider.isDirectoryAccessGranted() }
            )
    }

    private fun setRxErrorHandler() {
        RxJavaPlugins.setErrorHandler { t: Throwable ->
            var e = t
            if (e is UndeliverableException) {
                e = e.cause!!
            }
            if (e is IOException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return@setErrorHandler
            }
            if (e is InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return@setErrorHandler
            }
            if (e is NullPointerException || e is IllegalArgumentException) {
                // that's likely a bug in the application
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            if (e is IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            aapsLogger.warn(LTag.CORE, "Undeliverable exception received, not sure what to do", e.localizedMessage)
        }
    }

    private val timeDateReceiver = TimeDateOrTZChangeReceiver()
    private val networkReceiver = NetworkChangeReceiver()
    private val chargingReceiver = ChargingStateReceiver()
    private val btReceiver = BTReceiver()

    private fun registerLocalBroadcastReceiver() {
        var filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(timeDateReceiver, filter)
        filter = IntentFilter()
        @Suppress("DEPRECATION")
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(networkReceiver, filter)
        filter = IntentFilter()
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(chargingReceiver, filter)
        filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(btReceiver, filter)
    }

    private fun unregisterReceivers() {
        super.onTerminate()
        unregisterReceiver(timeDateReceiver)
        unregisterReceiver(networkReceiver)
        unregisterReceiver(chargingReceiver)
        unregisterReceiver(btReceiver)
    }

    private fun setupRemoteConfig() {
        FirebaseApp.initializeApp(this)
        Firebase.remoteConfig.also { firebaseRemoteConfig ->

            firebaseRemoteConfig.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings
                    .Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build()
            )
            firebaseRemoteConfig
                .fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        aapsLogger.debug("RemoteConfig received successfully")
                        @Suppress("UNCHECKED_CAST")
                        (versionCheckersUtils::class.declaredMemberProperties.find { it.name == "definition" } as KMutableProperty<Any>?)
                            ?.let {
                                // `definition` is read through reflection, so the cast below is unchecked and the
                                // compiler cannot see it. It said JSONObject long after the property became a
                                // kotlinx JsonObject, and the app crashed on start as soon as a remote config
                                // fetch actually succeeded - which needs network and Play Services, so no test or
                                // CI build ever reached it. Keep the two types here in step by hand.
                                val current = it.getter.call(versionCheckersUtils) as JsonObject
                                val remote = Json.parseToJsonElement(firebaseRemoteConfig.getString("defs")).jsonObject
                                // Plus on the maps is a shallow merge with the remote keys winning.
                                it.setter.call(versionCheckersUtils, JsonObject(current + remote))
                            }
                    } else aapsLogger.error("RemoteConfig fetch failed")
                }
        }
    }

    override fun onTerminate() {
        aapsLogger.debug(LTag.CORE, "onTerminate")
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
        unregisterReceivers()
        uiInteraction.stopAlarm("onTerminate")
        super.onTerminate()
    }

    /**
     * Teaches the resolvers which module owns which string names.
     * A `TextRef.Named` carries an owner and a name, and both the Compose and the ResourceHelper
     * paths need a way to turn that into an `R.string` id. `:core:keys`, `:core:interfaces` and
     * `:core:ui` are resolved directly because `:core:ui` sits above them, but a plugin or pump
     * module sits ABOVE `:core:ui`, so it can only be reached from here - `:app` is the one place
     * that depends on all of them.
     * Without this the lookup answers null and the raw name is drawn: `virtualpump_settings`
     * instead of "Virtual pump settings".
     */
}
