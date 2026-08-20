package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.IntKey
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.whenever

/**
 * Covers the pure surface of [ActionRunAutotune]: labels, icon/element type, dialog flag, and the
 * toJSON / fromJSON round-trip (incl. the tunedays default-from-preferences fallback). The
 * autotune-executing doAction()/isValid() paths need live plugins and are out of scope here.
 */
class ActionRunAutotuneTest : ActionsTestBase() {

    private lateinit var sut: ActionRunAutotune

    @BeforeEach
    fun setup() {
        whenever(rh.gs(anyInt(), anyVararg())).thenReturn("desc")
        sut = ActionRunAutotune(aapsLogger, rh, pumpEnactResultProvider, rh, autotunePlugin, profileFunction, activePlugin, preferences)
    }

    @Test fun friendlyName() {
        assertThat(sut.friendlyName()).isEqualTo(R.string.autotune_run)
    }

    @Test fun shortDescription() {
        assertThat(sut.shortDescription()).isEqualTo("desc")
    }

    @Test fun iconAndElementTypeAndDialog() {
        assertThat(sut.composeIcon()).isNotNull()
        assertThat(sut.elementType()).isEqualTo(ElementType.PROFILE_MANAGEMENT)
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSON_containsTypeAndData() {
        val json = JSONObject(sut.toJSON())
        assertThat(json.getString("type")).isEqualTo("ActionRunAutotune")
        val data = json.getJSONObject("data")
        assertThat(data.getString("profileToTune")).isEqualTo("")
        assertThat(data.getInt("tunedays")).isEqualTo(0)
        assertThat(data.getBoolean("MONDAY")).isTrue() // default all-days-on
    }

    @Test fun fromJSON_readsValues() {
        sut.fromJSON("""{"profileToTune":"myprofile","tunedays":3,"MONDAY":true,"TUESDAY":false}""")
        val data = JSONObject(sut.toJSON()).getJSONObject("data")
        assertThat(data.getString("profileToTune")).isEqualTo("myprofile")
        assertThat(data.getInt("tunedays")).isEqualTo(3)
        assertThat(data.getBoolean("TUESDAY")).isFalse()
    }

    @Test fun fromJSON_zeroTunedaysFallsBackToPreferenceDefault() {
        whenever(preferences.get(IntKey.AutotuneDefaultTuneDays)).thenReturn(5)
        sut.fromJSON("""{"profileToTune":"p","tunedays":0}""")
        val data = JSONObject(sut.toJSON()).getJSONObject("data")
        assertThat(data.getInt("tunedays")).isEqualTo(5)
    }
}
