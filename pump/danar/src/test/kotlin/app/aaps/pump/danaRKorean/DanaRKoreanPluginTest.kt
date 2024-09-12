package app.aaps.pump.danaRKorean

import android.content.SharedPreferences
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
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
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRKoreanPluginTest : TestBaseWithProfile() {

    @Mock lateinit var sharedPrefs: SharedPreferences
    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

    lateinit var danaPump: DanaPump

    private lateinit var danaRPlugin: DanaRKoreanPlugin

    init {
        addInjector {
            if (it is AdaptiveDoublePreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveIntPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
            if (it is AdaptiveIntentPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveUnitPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
            if (it is AdaptiveStringPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveListPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveListIntPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveClickPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
        }
    }

    @BeforeEach
    fun prepareMocks() {
        `when`(preferences.get(DanaStringKey.DanaMacAddress)).thenReturn("")
        `when`(preferences.get(DanaStringKey.DanaRName)).thenReturn("")
        `when`(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        `when`(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        `when`(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        danaPump = DanaPump(aapsLogger, preferences, dateUtil, instantiator, decimalFormatter)
        danaRPlugin = DanaRKoreanPlugin(
            aapsLogger, aapsSchedulers, rxBus, context, rh, constraintChecker, activePlugin, commandQueue, danaPump, dateUtil, fabricPrivacy,
            pumpSync, preferences, uiInteraction, danaHistoryDatabase, decimalFormatter, instantiator
        )
    }

    @Test @Throws(Exception::class)
    fun basalRateShouldBeLimited() {
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        danaRPlugin.applyBasalConstraints(c, validProfile)
        Assertions.assertEquals(0.8, c.value(), 0.01)
        Assertions.assertEquals("DanaRKorean: Limiting max basal rate to 0.80 U/h because of pump limit", c.getReasons())
        Assertions.assertEquals("DanaRKorean: Limiting max basal rate to 0.80 U/h because of pump limit", c.getMostLimitedReasons())
    }

    @Test @Throws(Exception::class)
    fun percentBasalRateShouldBeLimited() {
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        val c = ConstraintObject(Int.MAX_VALUE, aapsLogger)
        danaRPlugin.applyBasalPercentConstraints(c, validProfile)
        Assertions.assertEquals(200, c.value())
        Assertions.assertEquals("DanaRKorean: Limiting max percent rate to 200% because of pump limit", c.getReasons())
        Assertions.assertEquals("DanaRKorean: Limiting max percent rate to 200% because of pump limit", c.getMostLimitedReasons())
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        danaRPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}