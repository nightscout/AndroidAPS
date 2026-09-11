package app.aaps.wear

import android.content.Intent
import android.content.SharedPreferences
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.wear.comm.DataHandlerWear
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.comm.ExceptionHandlerWear
import app.aaps.wear.di.DaggerWearComponent
import app.aaps.wear.events.EventWearPreferenceChange
import app.aaps.wear.watchfaces.WatchFacePushHelper
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import app.aaps.wear.complications.cwf.CwfComplicationUpdater
import kotlinx.coroutines.launch
import javax.inject.Inject

class WearApp : DaggerApplication() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus

    @Suppress("unused")
    @Inject lateinit var dataHandlerWear: DataHandlerWear // instantiate only
    @Inject lateinit var exceptionHandlerWear: ExceptionHandlerWear
    @Inject lateinit var watchFacePushHelper: WatchFacePushHelper
    @Inject lateinit var cwfComplicationUpdater: CwfComplicationUpdater

    /**
     * Held in a field on purpose: `SharedPreferences` keeps its change listeners in a
     * `WeakHashMap`, so a listener nothing else references is garbage collected and preference
     * changes silently stop being announced from then on.
     *
     * The live watch faces never noticed - they redraw every second and pick preference changes up
     * themselves - but anything that only acts when told, such as the complication updater, simply
     * stopped being told.
     */
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        key ?: return@OnSharedPreferenceChangeListener
        // We trigger update on Complications
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
        rxBus.send(EventWearPreferenceChange(key))
    }

    override fun onCreate() {
        super.onCreate()
        exceptionHandlerWear.register()
        aapsLogger.debug(LTag.WEAR, "onCreate")
        // Keep an installed Watch Face Push face in sync with the app version (Wear OS 6+ only)
        CoroutineScope(Dispatchers.IO).launch { watchFacePushHelper.syncOnStartup() }
        // Refreshes the Custom watch face image complications when the picture changes, rather
        // than leaving them on the system's slow periodic timer
        cwfComplicationUpdater.start()
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        startForegroundService(Intent(this, DataLayerListenerServiceWear::class.java))
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> =
        DaggerWearComponent
            .builder()
            .application(this)
            .build()
}
