package app.aaps.wear

import android.content.Intent
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
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import javax.inject.Inject

class WearApp : DaggerApplication() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus

    @Suppress("unused")
    @Inject lateinit var dataHandlerWear: DataHandlerWear // instantiate only
    @Inject lateinit var exceptionHandlerWear: ExceptionHandlerWear

    override fun onCreate() {
        super.onCreate()
        exceptionHandlerWear.register()
        aapsLogger.debug(LTag.WEAR, "onCreate")
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener { _, key ->
            key ?: return@registerOnSharedPreferenceChangeListener
            // We trigger update on Complications
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
            rxBus.send(EventWearPreferenceChange(key))
        }
        startForegroundService(Intent(this, DataLayerListenerServiceWear::class.java))
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> =
        DaggerWearComponent
            .builder()
            .application(this)
            .build()
}
