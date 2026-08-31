package app.aaps.di.pump

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.pump.danars.comm.DanaRSPacket
import app.aaps.pump.danars.comm.DanaRSPacketNotifyAlarm
import app.aaps.pump.danars.comm.DanaRSPacketNotifyDeliveryComplete
import app.aaps.pump.danars.comm.DanaRSPacketNotifyDeliveryRateDisplay
import app.aaps.pump.danars.comm.DanaRSPacketNotifyMissedBolusAlarm
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

/**
 * The DanaRS command set and the Bluetooth adapter, on Metro. Was `DanaRSModule` + `DanaRSCommModule`.
 *
 * The set is built directly rather than through a qualified multibinding. The old shape used a javax
 * `@Qualifier` (`DanaRSCommModule.DanaRSCommand`) to keep these four packets apart from any other
 * `Set<DanaRSPacket>`, but `:app` runs Metro **without** Dagger interop, so a javax qualifier here would
 * simply not be read - the exact failure that put every constraint plugin in all three buckets once.
 * There is only one `Set<DanaRSPacket>` in the graph, so naming the four packets in one provider says
 * the same thing with nothing to misread.
 *
 * Only packets which are not a response to a sent packet belong here.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object DanaRSBindings {

    @Provides
    fun providesCommands(
        notifyAlarm: DanaRSPacketNotifyAlarm,
        notifyDeliveryComplete: DanaRSPacketNotifyDeliveryComplete,
        notifyDeliveryRateDisplay: DanaRSPacketNotifyDeliveryRateDisplay,
        notifyMissedBolusAlarm: DanaRSPacketNotifyMissedBolusAlarm
    ): Set<@JvmSuppressWildcards DanaRSPacket> =
        setOf(notifyAlarm, notifyDeliveryComplete, notifyDeliveryRateDisplay, notifyMissedBolusAlarm)

    @Provides
    fun providesBluetoothAdapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
}
