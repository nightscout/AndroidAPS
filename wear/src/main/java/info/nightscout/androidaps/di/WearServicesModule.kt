package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.comm.DataLayerListenerServiceWear
import info.nightscout.androidaps.complications.*
import info.nightscout.androidaps.heartrate.HeartRateListener
import info.nightscout.androidaps.tile.*
import info.nightscout.androidaps.watchfaces.*
import info.nightscout.androidaps.watchfaces.utils.BaseWatchFace

@Module
@Suppress("unused")
abstract class WearServicesModule {

    @ContributesAndroidInjector abstract fun contributesDataLayerListenerService(): DataLayerListenerServiceWear
    @ContributesAndroidInjector abstract fun contributesHeartRateListenerService(): HeartRateListener
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
    @ContributesAndroidInjector abstract fun contributesUploaderBatteryComplication(): UploaderBatteryComplication
    @ContributesAndroidInjector abstract fun contributesWallpaperComplication(): WallpaperComplication

    @ContributesAndroidInjector abstract fun contributesBaseWatchFace(): BaseWatchFace
    @ContributesAndroidInjector abstract fun contributesAapsWatchface(): AapsWatchface
    @ContributesAndroidInjector abstract fun contributesAapsV2Watchface(): AapsV2Watchface
    @ContributesAndroidInjector abstract fun contributesAapsLargeWatchface(): AapsLargeWatchface
    @ContributesAndroidInjector abstract fun contributesSteampunk(): SteampunkWatchface
    @ContributesAndroidInjector abstract fun contributesDigitalStyleWatchface(): DigitalStyleWatchface
    @ContributesAndroidInjector abstract fun contributesCockpitWatchface(): CockpitWatchface

    @ContributesAndroidInjector abstract fun contributesBIGChart(): BigChartWatchface
    @ContributesAndroidInjector abstract fun contributesNOChart(): NoChartWatchface
    @ContributesAndroidInjector abstract fun contributesCircleWatchface(): CircleWatchface

    @ContributesAndroidInjector abstract fun contributesTileBase(): TileBase
    @ContributesAndroidInjector abstract fun contributesQuickWizardTileService(): QuickWizardTileService
    @ContributesAndroidInjector abstract fun contributesTempTargetTileService(): TempTargetTileService
    @ContributesAndroidInjector abstract fun contributesActionsTileService(): ActionsTileService

}
