package app.aaps.shared.tests

import android.content.res.Resources
import android.content.res.TypedArray
import androidx.preference.PreferenceManager
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.objects.aps.DetermineBasalResult
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import app.aaps.implementation.instantiator.InstantiatorImpl
import app.aaps.implementation.profile.ProfileStoreObject
import app.aaps.implementation.profile.ProfileUtilImpl
import app.aaps.implementation.utils.DecimalFormatterImpl
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
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any

@Suppress("SpellCheckingInspection")
open class TestBaseWithProfile : TestBase() {

    @Mock open lateinit var activePlugin: ActivePlugin
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var processedTbrEbData: ProcessedTbrEbData
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var context: DaggerApplication
    @Mock lateinit var sp: SP
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var constraintsChecker: ConstraintsChecker
    @Mock lateinit var theme: Resources.Theme
    @Mock lateinit var typedArray: TypedArray

    lateinit var dateUtil: DateUtil
    lateinit var profileUtil: ProfileUtil
    lateinit var decimalFormatter: DecimalFormatter
    lateinit var hardLimits: HardLimits
    lateinit var instantiator: Instantiator

    private val injectors = mutableListOf<(Any) -> Unit>()
    fun addInjector(fn: (Any) -> Unit) {
        injectors.add(fn)
    }

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is DetermineBasalResult) {
                it.aapsLogger = aapsLogger
                it.constraintChecker = constraintsChecker
                it.preferences = preferences
                it.activePlugin = activePlugin
                it.processedTbrEbData = processedTbrEbData
                it.profileFunction = profileFunction
                it.rh = rh
                it.decimalFormatter = decimalFormatter
            }
            injectors.forEach { fn -> fn(it) }
        }
    }

    private lateinit var validProfileJSON: String
    private lateinit var invalidProfileJSON: String
    lateinit var preferenceManager: PreferenceManager
    lateinit var validProfile: ProfileSealed.Pure
    lateinit var effectiveProfileSwitch: EPS
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
        dateUtil = Mockito.spy(DateUtilImpl(context))
        decimalFormatter = DecimalFormatterImpl(rh)
        profileUtil = ProfileUtilImpl(preferences, decimalFormatter)
        testPumpPlugin = TestPumpPlugin(rh)
        Mockito.`when`(context.applicationContext).thenReturn(context)
        Mockito.`when`(context.androidInjector()).thenReturn(injector.androidInjector())
        Mockito.`when`(context.theme).thenReturn(theme)
        Mockito.`when`(context.obtainStyledAttributes(anyObject(), any(), any(), any())).thenReturn(typedArray)
        Mockito.`when`(dateUtil.now()).thenReturn(now)
        Mockito.`when`(activePlugin.activePump).thenReturn(testPumpPlugin)
        Mockito.`when`(preferences.get(StringKey.GeneralUnits)).thenReturn(GlucoseUnit.MGDL.asText)
        hardLimits = HardLimitsMock(sp, preferences, rh)
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

        Mockito.`when`(rh.gs(R.string.ok)).thenReturn("OK")
        Mockito.`when`(rh.gs(R.string.error)).thenReturn("Error")

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            String.format(rh.gs(string), arg1)
        }.`when`(rh).gs(anyInt(), anyInt())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            String.format(rh.gs(string), arg1)
        }.`when`(rh).gs(anyInt(), anyDouble())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            String.format(rh.gs(string), arg1)
        }.`when`(rh).gs(anyInt(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            val arg2 = invocation.getArgument<String?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyString(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyString(), anyInt())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            val arg2 = invocation.getArgument<String?>(2)

            // Use the safe call operator to handle potential null
            @Suppress("USELESS_ELVIS")
            val formattedString = rh.gs(string) ?: ""

            // Use a default value or handle null appropriately
            String.format(formattedString, arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyDouble(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyDouble(), anyInt())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyInt(), anyInt())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<String?>(2)
            String.format(rh.gs(string), arg1, arg2)
        }.`when`(rh).gs(anyInt(), anyInt(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.`when`(rh).gs(anyInt(), anyInt(), anyInt(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Int?>(1)
            val arg2 = invocation.getArgument<String?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.`when`(rh).gs(anyInt(), anyInt(), anyString(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<Double?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.`when`(rh).gs(anyInt(), anyDouble(), anyInt(), anyString())

        Mockito.doAnswer { invocation: InvocationOnMock ->
            val string = invocation.getArgument<Int>(0)
            val arg1 = invocation.getArgument<String?>(1)
            val arg2 = invocation.getArgument<Int?>(2)
            val arg3 = invocation.getArgument<String?>(3)
            String.format(rh.gs(string), arg1, arg2, arg3)
        }.`when`(rh).gs(anyInt(), anyString(), anyInt(), anyString())
        instantiator = InstantiatorImpl(injector, dateUtil, rh, aapsLogger, preferences, activePlugin, config, rxBus, hardLimits)
    }

    fun getValidProfileStore(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(validProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME)
        json.put("store", store)
        return ProfileStoreObject(json, aapsLogger, activePlugin, config, rh, rxBus, hardLimits, dateUtil)
    }

    fun getInvalidProfileStore1(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(invalidProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME)
        json.put("store", store)
        return ProfileStoreObject(json, aapsLogger, activePlugin, config, rh, rxBus, hardLimits, dateUtil)
    }

    fun getInvalidProfileStore2(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(validProfileJSON))
        store.put("invalid", JSONObject(invalidProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME + "invalid")
        json.put("store", store)
        return ProfileStoreObject(json, aapsLogger, activePlugin, config, rh, rxBus, hardLimits, dateUtil)
    }
}
