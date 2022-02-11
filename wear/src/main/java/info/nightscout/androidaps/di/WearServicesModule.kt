package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.complications.*
import info.nightscout.androidaps.data.ListenerService
import info.nightscout.androidaps.watchfaces.*

@Module
@Suppress("unused")
abstract class WearServicesModule {

    @ContributesAndroidInjector abstract fun contributesListenerService(): ListenerService

    @ContributesAndroidInjector abstract fun contributesBaseComplicationProviderService(): BaseComplicationProviderService
    @ContributesAndroidInjector abstract fun contributesBrCobIobComplication(): BrCobIobComplication
    @ContributesAndroidInjector abstract fun contributesCobDetailedComplication(): CobDetailedComplication
    @ContributesAndroidInjector abstract fun contributesCobIconComplication(): CobIconComplication
    @ContributesAndroidInjector abstract fun contributesCobIobComplication(): CobIobComplication
    @ContributesAndroidInjector abstract fun contributesComplicationTapBroadcastReceiver(): ComplicationTapBroadcastReceiver
    @ContributesAndroidInjector abstract fun contributesIobDetailedComplication(): IobDetailedComplication
    @ContributesAndroidInjector abstract fun contributesIobIconComplication(): IobIconComplication
    @ContributesAndroidInjector abstract fun contributesLongStatusComplication(): LongStatusComplication
    @ContributesAndroidInjector abstract fun contributesLongStatusFlippedComplication(): LongStatusFlippedComplication
    @ContributesAndroidInjector abstract fun contributesSgvComplication(): SgvComplication
    @ContributesAndroidInjector abstract fun contributesUploaderBattery(): UploaderBattery
    @ContributesAndroidInjector abstract fun contributesWallpaperComplication(): WallpaperComplication

    @ContributesAndroidInjector abstract fun contributesBaseWatchFace(): BaseWatchFace
    @ContributesAndroidInjector abstract fun contributesHome(): Home
    @ContributesAndroidInjector abstract fun contributesHome2(): Home2
    @ContributesAndroidInjector abstract fun contributesLargeHome(): LargeHome
    @ContributesAndroidInjector abstract fun contributesSteampunk(): Steampunk
    @ContributesAndroidInjector abstract fun contributesDigitalStyle(): DigitalStyle
    @ContributesAndroidInjector abstract fun contributesCockpit(): Cockpit
}