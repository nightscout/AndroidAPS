package app.aaps.ui.compose.careDialog

import androidx.lifecycle.SavedStateHandle
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class CareDialogViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock private lateinit var translator: Translator
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger

    private lateinit var sut: CareDialogViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(profileUtil.fromMgdlToUnits(any(), any())).thenReturn(0.0)
        whenever(dateUtil.now()).thenReturn(NOW)
        sut = CareDialogViewModel(
            SavedStateHandle(), persistenceLayer, batchExecutor, profileFunction, profileUtil,
            glucoseStatusProvider, translator, preferences, rh, dateUtil, aapsLogger,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `updateBgValue sets the value and auto-switches the meter from sensor to finger`() {
        assertThat(sut.uiState.value.meterType).isEqualTo(TE.MeterType.SENSOR)

        sut.updateBgValue(120.0)

        assertThat(sut.uiState.value.bgValue).isEqualTo(120.0)
        assertThat(sut.uiState.value.meterType).isEqualTo(TE.MeterType.FINGER)
    }

    @Test
    fun `updateDuration and updateNotes update the state`() {
        sut.updateDuration(30.0)
        sut.updateNotes("check")

        assertThat(sut.uiState.value.duration).isEqualTo(30.0)
        assertThat(sut.uiState.value.notes).isEqualTo("check")
    }

    @Test
    fun `updateEventTime records the time and marks it changed`() {
        sut.updateEventTime(123_456L)

        assertThat(sut.uiState.value.eventTime).isEqualTo(123_456L)
        assertThat(sut.uiState.value.eventTimeChanged).isTrue()
    }

    @Test
    fun `the dialog opens at now, with no offset`() {
        assertThat(sut.uiState.value.eventTime).isEqualTo(NOW)
        assertThat(sut.uiState.value.timeOffsetMinutes).isEqualTo(0)
        assertThat(sut.uiState.value.eventTimeChanged).isFalse()
    }

    /** The -/+ buttons, the same as in the bolus wizard, the carbs and the insulin dialog (#5353). */
    @Test
    fun `the minus and plus buttons move the time in minutes from now`() {
        sut.updateTimeOffset(-5)
        assertThat(sut.uiState.value.eventTime).isEqualTo(NOW - 5 * MINUTE)
        assertThat(sut.uiState.value.eventTimeChanged).isTrue()

        sut.updateTimeOffset(0)
        assertThat(sut.uiState.value.eventTime).isEqualTo(NOW)
        assertThat(sut.uiState.value.eventTimeChanged).isFalse()
    }

    @Test
    fun `the buttons stop at a week back and half a day ahead`() {
        sut.updateTimeOffset(-8 * 24 * 60)
        assertThat(sut.uiState.value.timeOffsetMinutes).isEqualTo(-7 * 24 * 60)
        assertThat(sut.uiState.value.eventTime).isEqualTo(NOW - 7 * 24 * 60 * MINUTE)

        sut.updateTimeOffset(13 * 60)
        assertThat(sut.uiState.value.timeOffsetMinutes).isEqualTo(12 * 60)
    }

    @Test
    fun `a picked time sets the offset, so the buttons go on from it`() {
        sut.updateEventTime(NOW - 37 * MINUTE)
        assertThat(sut.uiState.value.timeOffsetMinutes).isEqualTo(-37)

        sut.updateTimeOffset(sut.uiState.value.timeOffsetMinutes - 5)
        assertThat(sut.uiState.value.eventTime).isEqualTo(NOW - 42 * MINUTE)
    }

    private companion object {

        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
    }
}
