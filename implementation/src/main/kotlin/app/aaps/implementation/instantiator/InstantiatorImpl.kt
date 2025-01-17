package app.aaps.implementation.instantiator

import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.Preferences
import app.aaps.core.objects.aps.DetermineBasalResult
import app.aaps.implementation.iob.AutosensDataObject
import app.aaps.implementation.profile.ProfileStoreObject
import app.aaps.implementation.pump.PumpEnactResultObject
import dagger.Reusable
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

@Reusable
class InstantiatorImpl @Inject constructor(
    private val injector: HasAndroidInjector,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val activePlugin: ActivePlugin,
    private val config: Config,
    private val rxBus: RxBus,
    private val hardLimits: HardLimits
) : Instantiator {

    override fun provideProfileStore(jsonObject: JSONObject): ProfileStore = ProfileStoreObject(jsonObject, aapsLogger, activePlugin, config, rh, rxBus, hardLimits, dateUtil)
    override fun provideAPSResultObject(rt: RT): DetermineBasalResult = DetermineBasalResult(injector, rt)
    override fun provideAutosensDataObject(): AutosensData = AutosensDataObject(aapsLogger, preferences, dateUtil)
    override fun providePumpEnactResult(): PumpEnactResult = PumpEnactResultObject(rh)
}