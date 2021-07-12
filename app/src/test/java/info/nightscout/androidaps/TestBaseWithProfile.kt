package info.nightscout.androidaps

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.embedments.InsulinConfiguration
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import info.nightscout.androidaps.extensions.pureProfileFromJson
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import org.junit.Before
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest

@Suppress("SpellCheckingInspection")
@PrepareForTest(FabricPrivacy::class)
open class TestBaseWithProfile : TestBase() {

    @Mock lateinit var activePluginProvider: ActivePlugin
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var config: Config
    @Mock lateinit var context: Context

    lateinit var dateUtil: DateUtil
    val rxBus = RxBusWrapper(aapsSchedulers)

    val profileInjector = HasAndroidInjector { AndroidInjector { } }

    private lateinit var validProfileJSON: String
    lateinit var validProfile: ProfileSealed.Pure
    lateinit var effectiveProfileSwitch: EffectiveProfileSwitch

    @Suppress("PropertyName") val TESTPROFILENAME = "someProfile"

    @Before
    fun prepareMock() {
        validProfileJSON = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        dateUtil = DateUtil(context)
        validProfile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(validProfileJSON), dateUtil)!!)
        effectiveProfileSwitch = EffectiveProfileSwitch(
            timestamp = dateUtil.now(),
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = EffectiveProfileSwitch.GlucoseUnit.MMOL,
            originalProfileName = "",
            originalCustomizedName = "",
            originalTimeshift = 0,
            originalPercentage = 100,
            originalDuration = 0,
            originalEnd = 0,
            insulinConfiguration = InsulinConfiguration("", 0, 0)
        )
    }

    fun getValidProfileStore(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(validProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME)
        json.put("store", store)
        return ProfileStore(profileInjector, json, dateUtil)
    }
}