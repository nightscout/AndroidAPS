package app.aaps.plugins.automation.actions

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.R as CoreUiR
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.whenever

/**
 * Covers the pure surface of [ActionSettingsExport]: labels, icon/element type, validity, dialog
 * flag, and the text toJSON / fromJSON round-trip. The file-writing doAction() is out of scope.
 */
class ActionSettingsExportTest : ActionsTestBase() {

    private lateinit var sut: ActionSettingsExport

    @BeforeEach
    fun setup() {
        whenever(rh.gs(any<TextRef>(), anyVararg())).thenReturn("desc")
        sut = ActionSettingsExport(aapsLogger, rh, { pumpEnactResultProvider() }, rxBus, notificationManager, dateUtil, config, persistenceLayer, importExportPrefs, exportPasswordDataStore, preferences)
    }

    @Test fun friendlyName() {
        assertThat(sut.friendlyName()).isEqualTo(CoreUiStrings.exportsettings)
    }

    @Test fun shortDescription() {
        assertThat(sut.shortDescription()).isEqualTo("desc")
    }

    @Test fun iconElementValidityDialog() {
        assertThat(sut.composeIcon()).isNotNull()
        assertThat(sut.elementType()).isEqualTo(ElementType.SETTINGS)
        assertThat(sut.isValid()).isTrue()
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSON_defaultEmptyText() {
        val data = JSONObject(sut.toJSON()).getJSONObject("data")
        assertThat(data.getString("text")).isEqualTo("")
    }

    @Test fun jsonRoundTrip() {
        sut.fromJSON("""{"text":"hello"}""")
        val json = JSONObject(sut.toJSON())
        assertThat(json.getString("type")).isEqualTo("ActionSettingsExport")
        assertThat(json.getJSONObject("data").getString("text")).isEqualTo("hello")
    }
}
