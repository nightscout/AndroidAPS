package app.aaps.wear.di

import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.complications.BaseComplicationProviderService
import app.aaps.wear.complications.BrIobComplication
import app.aaps.wear.complications.BrCobIobComplication
import app.aaps.wear.complications.CobDetailedComplication
import app.aaps.wear.complications.CobIconComplication
import app.aaps.wear.complications.CobIobComplication
import app.aaps.wear.complications.ComplicationTapBroadcastReceiver
import app.aaps.wear.complications.IobDetailedComplication
import app.aaps.wear.complications.IobIconComplication
import app.aaps.wear.complications.LongStatusComplication
import app.aaps.wear.complications.LongStatusFlippedComplication
import app.aaps.wear.complications.SgvComplication
import app.aaps.wear.complications.UploaderBatteryComplication
import app.aaps.wear.complications.WallpaperComplication
import app.aaps.wear.heartrate.HeartRateListener
import app.aaps.wear.tile.ActionsTileService
import app.aaps.wear.tile.QuickWizardTileService
import app.aaps.wear.tile.TempTargetTileService
import app.aaps.wear.tile.TileBase
import app.aaps.wear.tile.UserActionTileService
import app.aaps.wear.watchfaces.CircleWatchface
import app.aaps.wear.watchfaces.CustomWatchface
import app.aaps.wear.watchfaces.DigitalStyleWatchface
import app.aaps.wear.watchfaces.utils.BaseWatchFace
import app.aaps.wear.wearStepCount.StepCountListener
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class WearServicesModule {

    @ContributesAndroidInjector abstract fun contributesDataLayerListenerService(): DataLayerListenerServiceWear
    @ContributesAndroidInjector abstract fun contributesHeartRateListenerService(): HeartRateListener
    @ContributesAndroidInjector abstract fun contributesStepsCountListenerService(): StepCountListener
    @ContributesAndroidInjector abstract fun contributesBaseComplicationProviderService(): BaseComplicationProviderService
    @ContributesAndroidInjector abstract fun contributesBrCobIobComplication(): BrCobIobComplication
    @ContributesAndroidInjector abstract fun contributesBrIobComplication(): BrIobComplication
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
    @ContributesAndroidInjector abstract fun contributesDigitalStyleWatchface(): DigitalStyleWatchface
    @ContributesAndroidInjector abstract fun contributesCircleWatchface(): CircleWatchface
    @ContributesAndroidInjector abstract fun contributesCustomWatchface(): CustomWatchface

    @ContributesAndroidInjector abstract fun contributesTileBase(): TileBase
    @ContributesAndroidInjector abstract fun contributesQuickWizardTileService(): QuickWizardTileService
    @ContributesAndroidInjector abstract fun contributesUserActionTileService(): UserActionTileService
    @ContributesAndroidInjector abstract fun contributesTempTargetTileService(): TempTargetTileService
    @ContributesAndroidInjector abstract fun contributesActionsTileService(): ActionsTileService

}
