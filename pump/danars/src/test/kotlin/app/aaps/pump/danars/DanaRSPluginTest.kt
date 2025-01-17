package app.aaps.pump.danars

import android.content.SharedPreferences
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.validators.preferences.AdaptiveClickPreference
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.core.validators.preferences.AdaptiveUnitPreference
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaStringKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito

@Suppress("SpellCheckingInspection")
class DanaRSPluginTest : DanaRSTestBase() {

    @Mock lateinit var sharedPreferences: SharedPreferences
    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

    private lateinit var danaRSPlugin: DanaRSPlugin

    init {
        addInjector {
            if (it is AdaptiveDoublePreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
            }
            if (it is AdaptiveIntPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
                it.config = config
            }
            if (it is AdaptiveIntentPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
            }
            if (it is AdaptiveUnitPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
            }
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
                it.config = config
            }
            if (it is AdaptiveStringPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
            }
            if (it is AdaptiveListPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
            }
            if (it is AdaptiveListIntPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
            }
            if (it is AdaptiveClickPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPreferences
            }
        }
    }


    @Test
    fun basalRateShouldBeLimited() {
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        danaRSPlugin.applyBasalConstraints(c, validProfile)
        Assertions.assertEquals(java.lang.Double.valueOf(0.8), c.value(), 0.0001)
        Assertions.assertEquals("DanaRS: limitingbasalratio", c.getReasons())
        Assertions.assertEquals("DanaRS: limitingbasalratio", c.getMostLimitedReasons())
    }

    @Test
    fun percentBasalRateShouldBeLimited() {
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRSPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = ConstraintObject(Int.MAX_VALUE, aapsLogger)
        danaRSPlugin.applyBasalPercentConstraints(c, validProfile)
        Assertions.assertEquals(200, c.value())
        Assertions.assertEquals("DanaRS: limitingpercentrate", c.getReasons())
        Assertions.assertEquals("DanaRS: limitingpercentrate", c.getMostLimitedReasons())
    }

    @BeforeEach
    fun prepareMocks() {
        Mockito.`when`(preferences.get(DanaStringKey.DanaRsName)).thenReturn("")
        Mockito.`when`(preferences.get(DanaStringKey.DanaMacAddress)).thenReturn("")
        Mockito.`when`(rh.gs(eq(app.aaps.core.ui.R.string.limitingbasalratio), anyObject(), anyObject())).thenReturn("limitingbasalratio")
        Mockito.`when`(rh.gs(eq(app.aaps.core.ui.R.string.limitingpercentrate), anyObject(), anyObject())).thenReturn("limitingpercentrate")

        danaRSPlugin =
            DanaRSPlugin(
                aapsLogger,
                aapsSchedulers,
                rxBus,
                context,
                rh,
                constraintChecker,
                profileFunction,
                commandQueue,
                danaPump,
                pumpSync,
                preferences,
                detailedBolusInfoStorage,
                temporaryBasalStorage,
                fabricPrivacy,
                dateUtil,
                uiInteraction,
                danaHistoryDatabase,
                decimalFormatter,
                instantiator
            )
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        danaRSPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}