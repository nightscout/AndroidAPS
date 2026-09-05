package app.aaps.wear

import android.app.Application
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        injectMetroMembers(this)
        exceptionHandlerWear.register()
        aapsLogger.debug(LTag.WEAR, "onCreate")
        // Keep an installed Watch Face Push face in sync with the app version (Wear OS 6+ only)
        CoroutineScope(Dispatchers.IO).launch { watchFacePushHelper.syncOnStartup() }
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener { _, key ->
            key ?: return@registerOnSharedPreferenceChangeListener
            // We trigger update on Complications
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
            rxBus.send(EventWearPreferenceChange(key))
        }
        startForegroundService(Intent(this, DataLayerListenerServiceWear::class.java))
    }
}
