package app.aaps.wear.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.wear.WearApp
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.complications.BgGraphComplication
import app.aaps.wear.complications.BrCobIobComplication
import app.aaps.wear.complications.BrCobIobComplicationExt1
import app.aaps.wear.complications.BrCobIobComplicationExt2
import app.aaps.wear.complications.BrComplication
import app.aaps.wear.complications.BrIobComplication
import app.aaps.wear.complications.BrTtComplication
import app.aaps.wear.complications.CobDetailedComplication
import app.aaps.wear.complications.CobIconComplication
import app.aaps.wear.complications.CobIobComplication
import app.aaps.wear.complications.ComplicationTapActivity
import app.aaps.wear.complications.IobDetailedComplication
import app.aaps.wear.complications.IobIconComplication
import app.aaps.wear.complications.LongStatusComplication
import app.aaps.wear.complications.LongStatusFlippedComplication
import app.aaps.wear.complications.SgvComplication
import app.aaps.wear.complications.SgvComplicationExt1
import app.aaps.wear.complications.SgvComplicationExt2
import app.aaps.wear.complications.SgvLargeComplication
import app.aaps.wear.complications.TargetComplication
import app.aaps.wear.complications.UploaderBatteryComplication
import app.aaps.wear.complications.WallpaperDarkComplication
import app.aaps.wear.complications.WallpaperGrayComplication
import app.aaps.wear.complications.WallpaperLightComplication
import app.aaps.wear.interaction.ConfigurationActivity
import app.aaps.wear.interaction.WatchfaceConfigurationActivity
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import app.aaps.wear.interaction.actions.BolusActivity
import app.aaps.wear.interaction.actions.CarbActivity
import app.aaps.wear.interaction.actions.ECarbActivity
import app.aaps.wear.interaction.actions.FillActivity
import app.aaps.wear.interaction.actions.ProfileSwitchActivity
import app.aaps.wear.interaction.actions.QuickSnoozeActivity
import app.aaps.wear.interaction.actions.RunningModeTimedActivity
import app.aaps.wear.interaction.actions.TempTargetActivity
import app.aaps.wear.interaction.actions.TreatmentActivity
import app.aaps.wear.interaction.actions.WizardActivity
import app.aaps.wear.interaction.activities.BgGraphActivity
import app.aaps.wear.interaction.activities.LoopStatusActivity
import app.aaps.wear.interaction.menus.FillMenuActivity
import app.aaps.wear.interaction.menus.MainMenuActivity
import app.aaps.wear.interaction.menus.PreferenceMenuActivity
import app.aaps.wear.interaction.menus.StatusMenuActivity
import app.aaps.wear.interaction.menus.TileMenuActivity
import app.aaps.wear.tile.ActionsTileService
import app.aaps.wear.tile.ActionsTileSettingsActivity
import app.aaps.wear.tile.BgGraphTileService
import app.aaps.wear.tile.BgGraphTileSettingsActivity
import app.aaps.wear.tile.QuickWizardTileService
import app.aaps.wear.tile.RunningModeTileService
import app.aaps.wear.tile.SceneTileService
import app.aaps.wear.tile.SceneTileSettingsActivity
import app.aaps.wear.tile.TempTargetTileService
import app.aaps.wear.tile.TempTargetTileSettingsActivity
import app.aaps.wear.tile.TileBase
import app.aaps.wear.tile.UserActionTileService
import app.aaps.wear.watchfaces.CircleWatchface
import app.aaps.wear.watchfaces.CustomWatchface
import app.aaps.wear.watchfaces.DigitalStyleWatchface
import app.aaps.wear.watchfaces.utils.BaseWatchFace
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for every Android entry point in this module - the `@ContributesAndroidInjector`
 * replacement, one line per class exactly as before.
 *
 * Android builds activities, services and watch faces itself, so they cannot take dependencies in a
 * constructor. `WearMetroActivity` and `WearMetroService` call `injectMetroMembers` and land here.
 *
 * A class missing from this map fails on launch with its own name, where `dagger.android` left the
 * fields unset and failed later at the first use.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object WearMemberInjectors {

    /**
     * The application itself. `DaggerApplication` injected it through `applicationInjector()`; with that
     * gone it goes through the same map as everything else.
     */
    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(WearApp::class)
    fun bindWearApp(injector: MembersInjector<WearApp>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ActionsTileService::class)
    fun bindActionsTileService(injector: MembersInjector<ActionsTileService>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ActionsTileSettingsActivity::class)
    fun bindActionsTileSettingsActivity(injector: MembersInjector<ActionsTileSettingsActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BackgroundActionActivity::class)
    fun bindBackgroundActionActivity(injector: MembersInjector<BackgroundActionActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BaseWatchFace::class)
    fun bindBaseWatchFace(injector: MembersInjector<BaseWatchFace>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BgGraphActivity::class)
    fun bindBgGraphActivity(injector: MembersInjector<BgGraphActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BgGraphComplication::class)
    fun bindBgGraphComplication(injector: MembersInjector<BgGraphComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BgGraphTileService::class)
    fun bindBgGraphTileService(injector: MembersInjector<BgGraphTileService>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BgGraphTileSettingsActivity::class)
    fun bindBgGraphTileSettingsActivity(injector: MembersInjector<BgGraphTileSettingsActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BolusActivity::class)
    fun bindBolusActivity(injector: MembersInjector<BolusActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BrCobIobComplication::class)
    fun bindBrCobIobComplication(injector: MembersInjector<BrCobIobComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BrCobIobComplicationExt1::class)
    fun bindBrCobIobComplicationExt1(injector: MembersInjector<BrCobIobComplicationExt1>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BrCobIobComplicationExt2::class)
    fun bindBrCobIobComplicationExt2(injector: MembersInjector<BrCobIobComplicationExt2>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BrComplication::class)
    fun bindBrComplication(injector: MembersInjector<BrComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BrIobComplication::class)
    fun bindBrIobComplication(injector: MembersInjector<BrIobComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(BrTtComplication::class)
    fun bindBrTtComplication(injector: MembersInjector<BrTtComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CarbActivity::class)
    fun bindCarbActivity(injector: MembersInjector<CarbActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CircleWatchface::class)
    fun bindCircleWatchface(injector: MembersInjector<CircleWatchface>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CobDetailedComplication::class)
    fun bindCobDetailedComplication(injector: MembersInjector<CobDetailedComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CobIconComplication::class)
    fun bindCobIconComplication(injector: MembersInjector<CobIconComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CobIobComplication::class)
    fun bindCobIobComplication(injector: MembersInjector<CobIobComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ComplicationTapActivity::class)
    fun bindComplicationTapActivity(injector: MembersInjector<ComplicationTapActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ConfigurationActivity::class)
    fun bindConfigurationActivity(injector: MembersInjector<ConfigurationActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(CustomWatchface::class)
    fun bindCustomWatchface(injector: MembersInjector<CustomWatchface>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(DataLayerListenerServiceWear::class)
    fun bindDataLayerListenerServiceWear(injector: MembersInjector<DataLayerListenerServiceWear>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(DigitalStyleWatchface::class)
    fun bindDigitalStyleWatchface(injector: MembersInjector<DigitalStyleWatchface>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ECarbActivity::class)
    fun bindECarbActivity(injector: MembersInjector<ECarbActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(FillActivity::class)
    fun bindFillActivity(injector: MembersInjector<FillActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(FillMenuActivity::class)
    fun bindFillMenuActivity(injector: MembersInjector<FillMenuActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(IobDetailedComplication::class)
    fun bindIobDetailedComplication(injector: MembersInjector<IobDetailedComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(IobIconComplication::class)
    fun bindIobIconComplication(injector: MembersInjector<IobIconComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(LongStatusComplication::class)
    fun bindLongStatusComplication(injector: MembersInjector<LongStatusComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(LongStatusFlippedComplication::class)
    fun bindLongStatusFlippedComplication(injector: MembersInjector<LongStatusFlippedComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(LoopStatusActivity::class)
    fun bindLoopStatusActivity(injector: MembersInjector<LoopStatusActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(MainMenuActivity::class)
    fun bindMainMenuActivity(injector: MembersInjector<MainMenuActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(PreferenceMenuActivity::class)
    fun bindPreferenceMenuActivity(injector: MembersInjector<PreferenceMenuActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(ProfileSwitchActivity::class)
    fun bindProfileSwitchActivity(injector: MembersInjector<ProfileSwitchActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(QuickSnoozeActivity::class)
    fun bindQuickSnoozeActivity(injector: MembersInjector<QuickSnoozeActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(QuickWizardTileService::class)
    fun bindQuickWizardTileService(injector: MembersInjector<QuickWizardTileService>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(RunningModeTileService::class)
    fun bindRunningModeTileService(injector: MembersInjector<RunningModeTileService>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(RunningModeTimedActivity::class)
    fun bindRunningModeTimedActivity(injector: MembersInjector<RunningModeTimedActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SceneTileService::class)
    fun bindSceneTileService(injector: MembersInjector<SceneTileService>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SceneTileSettingsActivity::class)
    fun bindSceneTileSettingsActivity(injector: MembersInjector<SceneTileSettingsActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SgvComplication::class)
    fun bindSgvComplication(injector: MembersInjector<SgvComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SgvComplicationExt1::class)
    fun bindSgvComplicationExt1(injector: MembersInjector<SgvComplicationExt1>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SgvComplicationExt2::class)
    fun bindSgvComplicationExt2(injector: MembersInjector<SgvComplicationExt2>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(SgvLargeComplication::class)
    fun bindSgvLargeComplication(injector: MembersInjector<SgvLargeComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(StatusMenuActivity::class)
    fun bindStatusMenuActivity(injector: MembersInjector<StatusMenuActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(TargetComplication::class)
    fun bindTargetComplication(injector: MembersInjector<TargetComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(TempTargetActivity::class)
    fun bindTempTargetActivity(injector: MembersInjector<TempTargetActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(TempTargetTileService::class)
    fun bindTempTargetTileService(injector: MembersInjector<TempTargetTileService>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(TempTargetTileSettingsActivity::class)
    fun bindTempTargetTileSettingsActivity(injector: MembersInjector<TempTargetTileSettingsActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(TileBase::class)
    fun bindTileBase(injector: MembersInjector<TileBase>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(TileMenuActivity::class)
    fun bindTileMenuActivity(injector: MembersInjector<TileMenuActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(TreatmentActivity::class)
    fun bindTreatmentActivity(injector: MembersInjector<TreatmentActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(UploaderBatteryComplication::class)
    fun bindUploaderBatteryComplication(injector: MembersInjector<UploaderBatteryComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(UserActionTileService::class)
    fun bindUserActionTileService(injector: MembersInjector<UserActionTileService>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(WallpaperDarkComplication::class)
    fun bindWallpaperDarkComplication(injector: MembersInjector<WallpaperDarkComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(WallpaperGrayComplication::class)
    fun bindWallpaperGrayComplication(injector: MembersInjector<WallpaperGrayComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(WallpaperLightComplication::class)
    fun bindWallpaperLightComplication(injector: MembersInjector<WallpaperLightComplication>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(WatchfaceConfigurationActivity::class)
    fun bindWatchfaceConfigurationActivity(injector: MembersInjector<WatchfaceConfigurationActivity>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(WizardActivity::class)
    fun bindWizardActivity(injector: MembersInjector<WizardActivity>): MembersInjector<*> = injector
}
