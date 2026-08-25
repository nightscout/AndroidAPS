package app.aaps.ui.compose.tempTarget

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.tempTargets.toJson
import app.aaps.core.interfaces.tempTargets.toTTPresets
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class TempTargetManagementViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var batchExecutor: BatchExecutor
    @Mock private lateinit var config: Config

    private lateinit var sut: TempTargetManagementViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        // init observers must be built (their launchIn is deferred); loadData() launch is deferred too.
        whenever(persistenceLayer.observeChanges(TT::class.java)).thenReturn(emptyFlow())
        whenever(preferences.observe(StringNonKey.TempTargetPresets)).thenReturn(MutableStateFlow("[]"))
        sut = TempTargetManagementViewModel(
            persistenceLayer, profileFunction, profileUtil, preferences, rh, dateUtil, aapsLogger,
            rxBus, batchExecutor, config, CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `editor setters update the state`() {
        sut.updateEditorName("Morning")
        sut.updateEditorTarget(5.5)
        sut.updateEditorDuration(3_600_000L)
        sut.updateNotes("note")
        sut.updateCurrentCardIndex(2)

        val state = sut.uiState.value
        assertThat(state.editorName).isEqualTo("Morning")
        assertThat(state.editorTarget).isEqualTo(5.5)
        assertThat(state.editorDuration).isEqualTo(3_600_000L)
        assertThat(state.notes).isEqualTo("note")
        assertThat(state.currentCardIndex).isEqualTo(2)
    }

    @Test
    fun `updateEventTime records the time and marks it changed`() {
        sut.updateEventTime(123_456L)

        assertThat(sut.uiState.value.eventTime).isEqualTo(123_456L)
        assertThat(sut.uiState.value.eventTimeChanged).isTrue()
    }

    @Test
    fun `hasUnsavedChanges is false with no selected preset`() {
        assertThat(sut.hasUnsavedChanges()).isFalse()
        assertThat(sut.isEditorDifferentFromDefaults()).isFalse()
    }

    /** The built-ins exactly as seeded at migration: whole-mg/dL targets and the default durations. */
    private val defaultBuiltIns = listOf(
        TTPreset(
            id = "eating_soon", reason = TT.Reason.EATING_SOON, isDeletable = false,
            targetValue = Constants.DEFAULT_TT_EATING_SOON_TARGET,
            duration = Constants.DEFAULT_TT_EATING_SOON_DURATION * 60L * 1000L
        ),
        TTPreset(
            id = "activity", reason = TT.Reason.ACTIVITY, isDeletable = false,
            targetValue = Constants.DEFAULT_TT_ACTIVITY_TARGET,
            duration = Constants.DEFAULT_TT_ACTIVITY_DURATION * 60L * 1000L
        ),
        TTPreset(
            id = "hypo", reason = TT.Reason.HYPOGLYCEMIA, isDeletable = false,
            targetValue = Constants.DEFAULT_TT_HYPO_TARGET,
            duration = Constants.DEFAULT_TT_HYPO_DURATION * 60L * 1000L
        )
    )

    private suspend fun TestScope.givenBuiltInsInMmol() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MMOL)
        whenever(profileUtil.fromMgdlToUnits(any(), any())).thenAnswer { inv ->
            (inv.arguments[0] as Double) * Constants.MGDL_TO_MMOLL
        }
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn(defaultBuiltIns.toJson())
        sut.loadData()
        advanceUntilIdle()
    }

    @Test
    fun `a freshly loaded preset reports no unsaved changes in mmol`() = runTest {
        // Regression: the editor holds a display-rounded value, so converting it back to mg/dL and
        // comparing against full-precision storage flagged an edit nobody made — 90 mg/dL shows as
        // 5.0 mmol/L, which converts back to 90.08 mg/dL. Every whole-mg/dL preset looked dirty,
        // which showed Save on load and (via its gate) disabled Reorder.
        givenBuiltInsInMmol()

        defaultBuiltIns.indices.forEach { index ->
            sut.selectPreset(index)
            assertThat(sut.hasUnsavedChanges()).isFalse()
            assertThat(sut.isEditorDifferentFromDefaults()).isFalse()
        }
    }

    @Test
    fun `editing the target by one display step is still detected in mmol`() = runTest {
        givenBuiltInsInMmol()
        sut.selectPreset(0)

        // 90 mg/dL displays as 5.0 mmol/L; one step up must not be swallowed by the tolerance.
        sut.updateEditorTarget(sut.uiState.value.editorTarget + 0.1)

        assertThat(sut.hasUnsavedChanges()).isTrue()
        assertThat(sut.isEditorDifferentFromDefaults()).isTrue()
    }

    // ---------------------------------------------------------------------------------------------
    // Reorder ("sort") mode
    // ---------------------------------------------------------------------------------------------

    private fun preset(id: String, reason: TT.Reason, deletable: Boolean) = TTPreset(
        id = id,
        name = if (deletable) id else null,
        reason = reason,
        targetValue = 100.0,
        duration = 3_600_000L,
        isDeletable = deletable
    )

    /** The three built-ins keep their positions; the two customs after them are movable. */
    private val storedPresets = listOf(
        preset("eating_soon", TT.Reason.EATING_SOON, deletable = false),
        preset("activity", TT.Reason.ACTIVITY, deletable = false),
        preset("hypo", TT.Reason.HYPOGLYCEMIA, deletable = false),
        preset("custom_a", TT.Reason.CUSTOM, deletable = true),
        preset("custom_b", TT.Reason.CUSTOM, deletable = true)
    )

    /** Drive the real load path so uiState carries [presets]. */
    private suspend fun TestScope.givenPresets(presets: List<TTPreset> = storedPresets) {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn(presets.toJson())
        sut.loadData()
        advanceUntilIdle()
    }

    private fun presetIdsPassedToPreferences(): List<String> {
        val captor = argumentCaptor<String>()
        verify(preferences).put(eq(StringNonKey.TempTargetPresets), captor.capture())
        return captor.lastValue.toTTPresets().map { it.id }
    }

    @Test
    fun `reorderOrder is null until reorder mode is entered`() {
        assertThat(sut.reorderOrder.value).isNull()
    }

    @Test
    fun `canReorder needs more than one movable preset`() = runTest {
        // Only the three built-ins: nothing a user could rearrange.
        givenPresets(storedPresets.take(3))
        assertThat(sut.canReorder()).isFalse()

        givenPresets(storedPresets)
        assertThat(sut.canReorder()).isTrue()
    }

    @Test
    fun `entering reorder mode starts from the identity order`() = runTest {
        givenPresets()
        sut.enterReorderMode()
        assertThat(sut.reorderOrder.value).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun `the three built-in presets are not movable`() = runTest {
        givenPresets()
        sut.enterReorderMode()

        assertThat(sut.isReorderPositionMovable(0)).isFalse()
        assertThat(sut.isReorderPositionMovable(1)).isFalse()
        assertThat(sut.isReorderPositionMovable(2)).isFalse()
        assertThat(sut.isReorderPositionMovable(3)).isTrue()
        assertThat(sut.isReorderPositionMovable(4)).isTrue()
    }

    @Test
    fun `a built-in preset cannot be moved`() = runTest {
        givenPresets()
        sut.enterReorderMode()

        assertThat(sut.moveReorderItem(0, 4)).isFalse()
        assertThat(sut.reorderOrder.value).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun `a custom preset cannot displace a built-in one`() = runTest {
        givenPresets()
        sut.enterReorderMode()

        // Position 2 holds the last built-in, so the first custom has nowhere earlier to go.
        assertThat(sut.moveReorderItem(3, 2)).isFalse()
        assertThat(sut.moveReorderItem(4, 0)).isFalse()
        assertThat(sut.reorderOrder.value).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun `custom presets can be reordered among themselves`() = runTest {
        givenPresets()
        sut.enterReorderMode()

        assertThat(sut.moveReorderItem(4, 3)).isTrue()
        assertThat(sut.reorderOrder.value).containsExactly(0, 1, 2, 4, 3).inOrder()
    }

    @Test
    fun `committing an unchanged order never writes the presets`() = runTest {
        givenPresets()
        clearInvocations(preferences)
        sut.enterReorderMode()

        sut.commitReorder()

        assertThat(sut.reorderOrder.value).isNull()
        // The preset list rides a bidirectionally synced preference, so a pointless write would be a
        // full round trip to the other device.
        verify(preferences, never()).put(eq(StringNonKey.TempTargetPresets), any())
    }

    @Test
    fun `committing writes the new order once, built-ins still first`() = runTest {
        givenPresets()
        clearInvocations(preferences)
        sut.enterReorderMode()
        sut.moveReorderItem(4, 3)

        sut.commitReorder()

        assertThat(presetIdsPassedToPreferences())
            .containsExactly("eating_soon", "activity", "hypo", "custom_b", "custom_a").inOrder()
    }

    @Test
    fun `committing settles on the card of the preset that was moved`() = runTest {
        givenPresets()
        sut.enterReorderMode()
        sut.moveReorderItem(4, 3)

        // No standalone active-TT card here, so the card index is the preset position.
        assertThat(sut.commitReorder()).isEqualTo(3)
    }

    @Test
    fun `a same-size replacement of the preset list aborts the commit`() = runTest {
        whenever(rh.gs(any<Int>())).thenReturn("message")
        givenPresets()
        sut.enterReorderMode()
        sut.moveReorderItem(4, 3)

        // Presets synced in from the master: same length, different entries. The working order is a
        // list of indices, so applying it would rearrange presets the user never saw.
        givenPresets(storedPresets.map { it.copy(id = "${it.id}_replaced") })
        clearInvocations(preferences)

        assertThat(sut.commitReorder()).isNull()
        assertThat(sut.reorderOrder.value).isNull()
        verify(preferences, never()).put(eq(StringNonKey.TempTargetPresets), any())
    }

    @Test
    fun `cancelling leaves the presets untouched`() = runTest {
        givenPresets()
        clearInvocations(preferences)
        sut.enterReorderMode()
        sut.moveReorderItem(4, 3)
        sut.cancelReorder()

        assertThat(sut.reorderOrder.value).isNull()
        verify(preferences, never()).put(eq(StringNonKey.TempTargetPresets), any())
    }

    @Test
    fun `moves and commits outside reorder mode do nothing`() = runTest {
        givenPresets()
        assertThat(sut.moveReorderItem(3, 4)).isFalse()
        assertThat(sut.isReorderPositionMovable(3)).isFalse()
        assertThat(sut.commitReorder()).isNull()
    }
}
