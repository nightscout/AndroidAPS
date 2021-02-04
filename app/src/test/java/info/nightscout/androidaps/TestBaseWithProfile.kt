package info.nightscout.androidaps

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ProfileStore
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.db.Treatment
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.rx.TestAapsSchedulers
import org.json.JSONObject
import org.junit.Before
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest

@PrepareForTest(FabricPrivacy::class)
open class TestBaseWithProfile : TestBase() {
    @Mock lateinit var activePluginProvider: ActivePluginProvider
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var treatmentsPlugin: TreatmentsPlugin
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var dateUtil: DateUtil

    val rxBus = RxBusWrapper(aapsSchedulers)

    val profileInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is Profile) {
                it.aapsLogger = aapsLogger
                it.activePlugin = activePluginProvider
                it.resourceHelper = resourceHelper
                it.rxBus = rxBus
                it.fabricPrivacy = fabricPrivacy
                it.configInterface = Config()
            }
            if (it is ProfileSwitch) {
                it.treatmentsPlugin = treatmentsPlugin
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.resourceHelper = resourceHelper
                it.dateUtil = dateUtil
            }
            if (it is Treatment) {
                it.activePlugin = activePluginProvider
                it.profileFunction = profileFunction
                it.defaultValueHelper = defaultValueHelper
                it.resourceHelper = resourceHelper
            }
        }
    }

    private lateinit var validProfileJSON: String
    lateinit var validProfile: Profile
    val TESTPROFILENAME = "someProfile"

    @Before
    fun prepareMock() {
        validProfileJSON = "{\"dia\":\"3\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"},{\"time\":\"2:00\",\"value\":\"110\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        validProfile = Profile(profileInjector, JSONObject(validProfileJSON), Constants.MGDL)
    }

    fun getValidProfileStore(): ProfileStore {
        val json = JSONObject()
        val store = JSONObject()
        store.put(TESTPROFILENAME, JSONObject(validProfileJSON))
        json.put("defaultProfile", TESTPROFILENAME)
        json.put("store", store)
        return ProfileStore(profileInjector, json)
    }
}