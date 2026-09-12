package app.aaps.wear

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.wear.comm.DataHandlerWear
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.comm.ExceptionHandlerWear
import app.aaps.wear.complications.cwf.CwfComplicationUpdater
import app.aaps.wear.di.WearGraph
import app.aaps.wear.events.EventWearPreferenceChange
import app.aaps.wear.watchfaces.WatchFacePushHelper
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WearApp : Application(), MetroMemberInjector {

    /** Built once, on first use, because Android constructs this class before anything can be injected. */
    private val graph: WearGraph by lazy { createGraphFactory<WearGraph.Factory>().create(this) }

    @Suppress("UNCHECKED_CAST")
    override fun injectMembers(target: Any): Boolean {
        val injector = graph.memberInjectors[target::class] ?: return false
        (injector as MembersInjector<Any>).injectMembers(target)
        return true
    }

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
        injectMetroMembers(this)
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
}
