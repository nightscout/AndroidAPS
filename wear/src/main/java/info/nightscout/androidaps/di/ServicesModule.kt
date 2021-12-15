package info.nightscout.androidaps.di

import android.net.wifi.hotspot2.pps.HomeSp
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.complications.BaseComplicationProviderService
import info.nightscout.androidaps.complications.BrCobIobComplication
import info.nightscout.androidaps.complications.LongStatusComplication
import info.nightscout.androidaps.complications.LongStatusFlippedComplication
import info.nightscout.androidaps.complications.SgvComplication
import info.nightscout.androidaps.data.ListenerService
import info.nightscout.androidaps.watchfaces.*

@Module
@Suppress("unused")
abstract class ServicesModule {

    @ContributesAndroidInjector abstract fun contributesListenerService(): ListenerService
    @ContributesAndroidInjector abstract fun contributesBaseComplicationProviderService(): BaseComplicationProviderService
    @ContributesAndroidInjector abstract fun contributesBrCobIobComplication(): BrCobIobComplication
    @ContributesAndroidInjector abstract fun contributesLongStatusComplication(): LongStatusComplication
    @ContributesAndroidInjector abstract fun contributesLongStatusFlippedComplication(): LongStatusFlippedComplication
    @ContributesAndroidInjector abstract fun contributesSgvComplication(): SgvComplication
    @ContributesAndroidInjector abstract fun contributesBaseWatchFace(): BaseWatchFace
    @ContributesAndroidInjector abstract fun contributesHome(): Home
    @ContributesAndroidInjector abstract fun contributesHome2(): Home2
    @ContributesAndroidInjector abstract fun contributesHomeSp(): HomeSp
    @ContributesAndroidInjector abstract fun contributesLargeHome(): LargeHome
    @ContributesAndroidInjector abstract fun contributesSteampunk(): Steampunk
    @ContributesAndroidInjector abstract fun contributesDigitalStyle(): DigitalStyle
    @ContributesAndroidInjector abstract fun contributesCockpit(): Cockpit
}