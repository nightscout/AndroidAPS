package app.aaps.pump.common.hw.rileylink.service

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.common.hw.rileylink.RileyLinkConst
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import dev.zacsweers.metro.Inject

class RileyLinkBluetoothStateReceiver : BroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rileyLinkUtil: RileyLinkUtil

    override fun onReceive(context: Context, intent: Intent) {
        // What MetroBroadcastReceiver does; this module does not depend on :core:objects.
        context.injectMetroMembers(this)
        val action = intent.action
        if (action != null) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_TURNING_ON -> {
                }

                BluetoothAdapter.STATE_ON                                                                         -> {
                    aapsLogger.debug("RileyLinkBluetoothStateReceiver: Bluetooth back on. Sending broadcast to RileyLink Framework")
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.BluetoothReconnected)
                }
            }
        }
    }

    fun unregisterBroadcasts(context: Context) {
        context.unregisterReceiver(this)
    }

    fun registerBroadcasts(context: Context) {
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(this, filter)
    }
}