package app.aaps.pump.danars.di

import app.aaps.pump.danars.comm.DanaRSPacket
import app.aaps.pump.danars.comm.DanaRSPacketNotifyAlarm
import app.aaps.pump.danars.comm.DanaRSPacketNotifyDeliveryComplete
import app.aaps.pump.danars.comm.DanaRSPacketNotifyDeliveryRateDisplay
import app.aaps.pump.danars.comm.DanaRSPacketNotifyMissedBolusAlarm
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Qualifier

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
interface DanaRSCommModule {

    /*
     * Only packets which are not respond to sent packet must be listed
     */
    @Binds
    @DanaRSCommand
    @IntoSet
    fun bindDanaRSPacketNotifyAlarm(packet: DanaRSPacketNotifyAlarm): DanaRSPacket

    @Binds
    @DanaRSCommand
    @IntoSet
    fun bindDanaRSPacketNotifyDeliveryComplete(packet: DanaRSPacketNotifyDeliveryComplete): DanaRSPacket

    @Binds
    @DanaRSCommand
    @IntoSet
    fun bindDanaRSPacketNotifyDeliveryRateDisplay(packet: DanaRSPacketNotifyDeliveryRateDisplay): DanaRSPacket

    @Binds
    @DanaRSCommand
    @IntoSet
    fun bindDanaRSPacketNotifyMissedBolusAlarm(packet: DanaRSPacketNotifyMissedBolusAlarm): DanaRSPacket

    @Qualifier
    annotation class DanaRSCommand
}