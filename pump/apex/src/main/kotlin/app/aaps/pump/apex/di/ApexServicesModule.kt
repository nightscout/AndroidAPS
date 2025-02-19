package app.aaps.pump.apex.di

import app.aaps.pump.apex.ApexService
import app.aaps.pump.apex.connectivity.ApexBluetooth
import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.pump.apex.misc.ApexDeviceInfoImpl
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class ApexServicesModule {
    @Binds abstract fun contributesApexDeviceInfo(apexDeviceInfoImpl: ApexDeviceInfoImpl): ApexDeviceInfo
    @ContributesAndroidInjector abstract fun contributesApexBluetooth(): ApexBluetooth
    @ContributesAndroidInjector abstract fun contributesApexService(): ApexService
}

