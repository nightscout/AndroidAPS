package app.aaps.shared.tests

import android.content.res.Resources
import android.content.res.TypedArray
import androidx.preference.PreferenceManager
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import app.aaps.core.validators.preferences.AdaptiveClickPreference
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.core.validators.preferences.AdaptiveUnitPreference
import app.aaps.implementation.aps.DetermineBasalResult
import app.aaps.implementation.profile.ProfileStoreObject
import app.aaps.implementation.profile.ProfileUtilImpl
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.implementation.utils.DecimalFormatterImpl
import app.aaps.plugins.aps.openAPS.DeltaCalculator
import app.aaps.plugins.aps.openAPSSMB.GlucoseStatusCalculatorSMB
import app.aaps.shared.impl.utils.DateUtilImpl
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import javax.inject.Provider

@Suppress("SpellCheckingInspection")
open class TestBaseWithProfile : TestBase() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var processedTbrEbData: ProcessedTbrEbData
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var context: DaggerApplication
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var constraintsChecker: ConstraintsChecker
    @Mock lateinit var theme: Resources.Theme
    @Mock lateinit var typedArray: TypedArray

    lateinit var dateUtil: DateUtil
    lateinit var profileUtil: ProfileUtil
    lateinit var decimalFormatter: DecimalFormatter
    lateinit var hardLimits: HardLimits
    lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>
    lateinit var profileStoreProvider: Provider<ProfileStore>
    lateinit var glucoseStatusCalculatorSMB: GlucoseStatusCalculatorSMB
    lateinit var deltaCalculator: DeltaCalculator
    lateinit var apsResultProvider: Provider<APSResult>

    val smbGlucoseStatusProvider = object : GlucoseStatusProvider {
        override val glucoseStatusData: GlucoseStatus?
            get() = getGlucoseStatusData(false)

        override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? = glucoseStatusCalculatorSMB.getGlucoseStatusData(allowOldData)

    }

    private val injectors = mutableListOf<(Any) -> Unit>()
    fun addInjector(fn: (Any) -> Unit) {
        injectors.add(fn)
    }

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is AdaptiveDoublePreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
            }
            if (it is AdaptiveIntPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.config = config
            }
            if (it is AdaptiveIntentPreference) {
                it.preferences = preferences
            }
            if (it is AdaptiveUnitPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
            }
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.config = config
            }
            if (it is AdaptiveStringPreference) {
                it.preferences = preferences
            }
            if (it is AdaptiveListPreference) {
                it.preferences = preferences
            }
            if (it is AdaptiveListIntPreference) {
                it.preferences = preferences
            }
            if (it is AdaptiveClickPreference) {
                it.preferences = preferences
            }
            injectors.forEach { fn -> fn(it) }
        }
    }

    private lateinit var validProfileJSON: String
    private lateinit var invalidProfileJSON: String
    lateinit var preferenceManager: PreferenceManager
    lateinit var validProfile: ProfileSealed.Pure
    lateinit var effectiveProfileSwitch: EPS
    lateinit var profileSwitch: PS
    lateinit var testPumpPlugin: TestPumpPlugin

    var now = 1656358822000L

    @Suppress("PropertyName") val TESTPROFILENAME = "someProfile"

    @BeforeEach
    fun prepareMock() {
        validProfileJSON = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"}," +
            "{\"time\":\"2:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}]," +
            "\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        invalidProfileJSON = "{\"dia\":\"1\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"}," +
            "{\"time\":\"2:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}]," +
            "\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        preferenceManager = PreferenceManager(context)
        dateUtil = spy(DateUtilImpl(context))
        decimalFormatter = DecimalFormatterImpl(rh)
        profileUtil = ProfileUtilImpl(preferences, decimalFormatter)
        testPumpPlugin = TestPumpPlugin(rh)
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.androidInjector()).thenReturn(injector.androidInjector())
        whenever(context.theme).thenReturn(theme)
        whenever(context.obtainStyledAttributes(anyOrNull(), any(), any(), any())).thenReturn(typedArray)
        whenever(dateUtil.now()).thenReturn(now)
        whenever(activePlugin.activePump).thenReturn(testPumpPlugin)
        whenever(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MGDL.asText)
        deltaCalculator = DeltaCalculator(aapsLogger)
        apsResultProvider = Provider { DetermineBasalResult(aapsLogger, constraintsChecker, preferences, activePlugin, processedTbrEbData, profileFunction, rh, decimalFormatter, dateUtil, apsResultProvider) }
        hardLimits = HardLimitsMock(preferences, rh)
        validProfile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(validProfileJSON), dateUtil)!!, activePlugin)
        effectiveProfileSwitch = EPS(
            timestamp = dateUtil.now(),
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = GlucoseUnit.MMOL,
            originalProfileName = "",
            originalCustomizedName = "",
            originalTimeshift = 0,
            originalPercentage = 100,
            originalDuration = 0,
            originalEnd = 0,
            iCfg = ICfg("", 0, 0)
        )
        profileSwitch = PS(
            timestamp = dateUtil.now(),
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = GlucoseUnit.MMOL,
            profileName = "",
            timeshift = 0,
            percentage = 100,
            duration = 0,
            iCfg = ICfg("", 0, 0)
        )

        whenever(rh.gs(R.string.ok)).thenReturn("OK")
        whenever(rh.gs(R.string.error)).thenReturn("Error")

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            String.format(rh.gs(string), arg1)
        }.whenever(rh).gs(anyInt(), anyInt())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            String.format(rh.gs(string), arg1)
        }.whenever(rh).gs(anyInt(), anyDouble())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            String.format(rh.gs(string), arg1)
        }.whenever(rh).gs(anyInt(), anyString())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            val arg2 = invocation.getArgument<String?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.whenever(rh).gs(anyInt(), anyString(), anyString())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.whenever(rh).gs(anyInt(), anyString(), anyInt())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            val arg2 = invocation.getArgument<String?>(2)

            // Use the safe call operator to handle potential null
            @Suppress("USELESS_ELVIS")
            val formattedString = rh.gs(string) ?: ""

            // Use a default value or handle null appropriately
            String.format(formattedString, arg1, arg2)
        }.whenever(rh).gs(anyInt(), anyDouble(), anyString())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.whenever(rh).gs(anyInt(), anyDouble(), anyInt())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.whenever(rh).gs(anyInt(), anyInt(), anyInt())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<String?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.whenever(rh).gs(anyInt(), anyInt(), anyString())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.whenever(rh).gs(anyInt(), anyInt(), anyInt(), anyString())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<String?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.whenever(rh).gs(anyInt(), anyInt(), anyString(), anyString())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.whenever(rh).gs(anyInt(), anyDouble(), anyInt(), anyString())

        doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.whenever(rh).gs(anyInt(), anyString(), anyInt(), anyString())
        pumpEnactResultProvider = Provider { PumpEnactResultObject(rh) }
        profileStoreProvider = Provider { ProfileStoreObject(aapsLogger, activePlugin, config, rh, rxBus, hardLimits, dateUtil) }
        glucoseStatusCalculatorSMB = GlucoseStatusCalculatorSMB(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, DeltaCalculator(aapsLogger))
    }

    fun getValidProfileStore(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(validProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME)
        json.put("store", store)
        return ProfileStoreObject(aapsLogger, activePlugin, config, rh, rxBus, hardLimits, dateUtil).with(json)
    }

    fun getInvalidProfileStore1(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(invalidProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME)
        json.put("store", store)
        return ProfileStoreObject(aapsLogger, activePlugin, config, rh, rxBus, hardLimits, dateUtil).with(json)
    }

    fun getInvalidProfileStore2(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(validProfileJSON))
        store.put("invalid", JSONObject(invalidProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME + "invalid")
        json.put("store", store)
        return ProfileStoreObject(aapsLogger, activePlugin, config, rh, rxBus, hardLimits, dateUtil).with(json)
    }
}
