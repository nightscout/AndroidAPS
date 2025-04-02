package app.aaps.plugins.automation.actions

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.keys.BooleanKey
import app.aaps.core.validators.preferences.AdaptiveDoublePreference
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.core.validators.preferences.AdaptiveUnitPreference

// only useful with active AutoISF

class ActionAutoisfEnableTest : ActionsTestBase() {

    lateinit var sut: ActionAutoisfEnable

    init {
        addInjector {
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
        }
    }
    @BeforeEach
    fun setup() {

        //testPumpPlugin.pumpDescription.isTempBasalCapable = true
        `when`(rh.gs(R.string.enableautoisf)).thenReturn("Enable glucose ISF weights(autoISF)")
        `when`(rh.gs(R.string.alreadyenabled)).thenReturn("Already enabled")

        sut = ActionAutoisfEnable(injector)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(R.string.enableautoisf)
    }

    @Test fun shortDescriptionTest() {
        assertThat(sut.shortDescription()).isEqualTo("Enable glucose ISF weights(autoISF)")
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(R.drawable.ic_autoisf_enabled)
    }

    @Test fun doActionTest() {
        `when`( preferences.get( BooleanKey.ApsUseAutoIsfWeights )).thenReturn(false)
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat( preferences.get( BooleanKey.ApsUseAutoIsfWeights )).isTrue()
            }
        })
        assertThat( preferences.get( BooleanKey.ApsUseAutoIsfWeights )).isTrue()
        //Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, true)
        //Mockito.verify(configBuilder, Mockito.times(1)).storeSettings("ActionLoopEnable")


        // another call should keep it enabled
        `when`( preferences.get( BooleanKey.ApsUseAutoIsfWeights )).thenReturn(true)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        assertThat( preferences.get( BooleanKey.ApsUseAutoIsfWeights )).isTrue()
        //Mockito.verify(loopPlugin, Mockito.times(1)).setPluginEnabled(PluginType.LOOP, true)
        //Mockito.verify(configBuilder, Mockito.times(1)).storeSettings("ActionLoopEnable")
    }
}
