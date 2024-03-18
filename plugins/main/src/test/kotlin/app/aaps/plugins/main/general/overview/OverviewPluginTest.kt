package app.aaps.plugins.main.general.overview

import android.app.Activity
import android.content.SharedPreferences
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.AdaptiveIntentPreference
import app.aaps.core.validators.AdaptiveDoublePreference
import app.aaps.core.validators.AdaptiveIntPreference
import app.aaps.core.validators.AdaptiveStringPreference
import app.aaps.core.validators.AdaptiveSwitchPreference
import app.aaps.core.validators.AdaptiveUnitPreference
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class OverviewPluginTest : TestBaseWithProfile() {

    @Mock lateinit var overviewData: OverviewData
    @Mock lateinit var overviewMenus: OverviewMenus
    @Mock lateinit var notificationStore: NotificationStore
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var sharedPrefs: SharedPreferences

    private lateinit var overviewPlugin: OverviewPlugin

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
            if (it is AdaptiveUnitPreference) {
                it.profileUtil = profileUtil
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveIntentPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveStringPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
            }
            if (it is AdaptiveSwitchPreference) {
                it.preferences = preferences
                it.sharedPrefs = sharedPrefs
                it.config = config
            }
        }
    }

    @BeforeEach fun prepare() {
        overviewPlugin = OverviewPlugin(
            injector, notificationStore, fabricPrivacy, rxBus, sp, preferences,
            aapsLogger, aapsSchedulers, rh, overviewData, overviewMenus, context, constraintsChecker, uiInteraction
        )
        Mockito.`when`(uiInteraction.quickWizardListActivity).thenReturn(Activity::class.java)
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        overviewPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
