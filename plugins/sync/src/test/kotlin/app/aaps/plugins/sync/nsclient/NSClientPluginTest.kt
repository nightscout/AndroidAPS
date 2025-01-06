package app.aaps.plugins.sync.nsclient

import android.content.SharedPreferences
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.core.validators.preferences.AdaptiveUnitPreference
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class NSClientPluginTest : TestBaseWithProfile() {

    @Mock lateinit var sharedPrefs: SharedPreferences
    @Mock lateinit var receiverDelegate: ReceiverDelegate
    @Mock lateinit var dataSyncSelectorV1: DataSyncSelectorV1
    @Mock lateinit var nsSettingsStatus: NSSettingsStatus

    private lateinit var nsClientPlugin: NSClientPlugin

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
        }
    }

    @BeforeEach fun prepare() {
        nsClientPlugin = NSClientPlugin(
            aapsLogger, aapsSchedulers, rxBus, rh, context, fabricPrivacy, sp, preferences, receiverDelegate, dataSyncSelectorV1,
            dateUtil, profileUtil, nsSettingsStatus, decimalFormatter
        )
    }

    @Test
    fun specialEnableConditionTest() {
        assertThat(nsClientPlugin.specialEnableCondition()).isTrue()
    }

    @Test
    fun specialShowInListConditionTest() {
        assertThat(nsClientPlugin.specialShowInListCondition()).isTrue()
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        nsClientPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
