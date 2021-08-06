package info.nightscout.androidaps

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.extensions.pureProfileFromJson
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.ProfileStore
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DefaultValueHelper
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
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var config: Config

    val rxBus = RxBusWrapper(aapsSchedulers)

    val profileInjector = HasAndroidInjector {
        AndroidInjector {
        }
    }

    private lateinit var validProfileJSON: String
    lateinit var validProfile: Profile
    @Suppress("PropertyName") val TESTPROFILENAME = "someProfile"

    @Before
    fun prepareMock() {
        validProfileJSON = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        validProfile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(validProfileJSON), dateUtil)!!)
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