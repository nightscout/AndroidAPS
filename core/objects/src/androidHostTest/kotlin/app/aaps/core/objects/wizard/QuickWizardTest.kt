package app.aaps.core.objects.wizard

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class QuickWizardTest : TestBaseWithProfile() {

    @Mock lateinit var loop: Loop
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var bolusWizard: BolusWizard
    @Mock lateinit var quickWizardMock: QuickWizard

    private val data1 = "{\"buttonText\":\"Meal\",\"carbs\":36,\"validFrom\":0,\"validTo\":18000," +
        "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":0,\"useBasalIOB\":0,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}"
    private val data2 = "{\"buttonText\":\"Lunch\",\"carbs\":18,\"validFrom\":36000,\"validTo\":39600," +
        "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":1,\"useBasalIOB\":2,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}"
    private var array: List<QuickWizardEntryData> = entries(data1, data2)

    /**
     * Keeps the test data as the JSON text a real preference holds, and parses it the same way the
     * production code does, so this still covers [QuickWizardEntryData.fromJsonObject].
     */
    private fun entries(vararg json: String): List<QuickWizardEntryData> =
        json.map { QuickWizardEntryData.fromJsonObject(Json.parseToJsonElement(it) as JsonObject) }

    class MockedTime : QuickWizardEntry.Time() {

        override fun secondsFromMidnight() = 0
    }

    private val mockedTime = MockedTime()
    private lateinit var quickWizard: QuickWizard

    @BeforeEach
    fun setup() {
        whenever(preferences.get(StringNonKey.QuickWizard)).thenReturn("[]")
        whenever(preferences.observe(StringNonKey.QuickWizard)).thenReturn(MutableStateFlow("[]"))
        val quickWizardEntry = QuickWizardEntry(aapsLogger, preferences, profileFunction, loop, iobCobCalculator, persistenceLayer, dateUtil, glucoseStatusProvider, { bolusWizard }, { quickWizardMock })
        quickWizardEntry.time = mockedTime
        quickWizard = QuickWizard(preferences) { quickWizardEntry }
    }

    @Test
    fun setDataTest() {
        quickWizard.setData(array)
        assertThat(quickWizard.size()).isEqualTo(2)
    }

    @Test
    fun test() {
        quickWizard.setData(array)
        assertThat(quickWizard[1].buttonText()).isEqualTo("Lunch")
    }

    @Test
    fun active() {
        quickWizard.setData(array)
        val e: QuickWizardEntry = quickWizard.getActive()!!
        assertThat(e.carbs().toDouble()).isWithin(0.01).of(36.0)
        quickWizard.remove(0)
        quickWizard.remove(0)
        assertThat(quickWizard.getActive()).isNull()
    }

    @Test
    fun newEmptyItemTest() {
        assertThat(quickWizard.newEmptyItem()).isNotNull()
    }

    @Test
    fun addOrUpdate() {
        quickWizard.setData(array)
        assertThat(quickWizard.size()).isEqualTo(2)
        quickWizard.addOrUpdate(quickWizard.newEmptyItem())
        assertThat(quickWizard.size()).isEqualTo(3)
        val q: QuickWizardEntry = quickWizard.newEmptyItem()
        q.position = 0
        quickWizard.addOrUpdate(q)
        assertThat(quickWizard.size()).isEqualTo(3)
    }

    @Test
    fun remove() {
        quickWizard.setData(array)
        quickWizard.remove(0)
        assertThat(quickWizard.size()).isEqualTo(1)
    }

    private val data3 = "{\"buttonText\":\"Dinner\",\"carbs\":24,\"validFrom\":0,\"validTo\":18000," +
        "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":0,\"useBasalIOB\":0,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}"

    private fun buttonTexts() = (0 until quickWizard.size()).map { quickWizard[it].buttonText() }

    @Test
    fun reorderAppliesThePermutation() {
        quickWizard.setData(entries(data1, data2, data3))

        // order[newIndex] == oldIndex, so this reads "Dinner, Meal, Lunch".
        assertThat(quickWizard.reorder(listOf(2, 0, 1))).isTrue()

        assertThat(buttonTexts()).containsExactly("Dinner", "Meal", "Lunch").inOrder()
    }

    @Test
    fun reorderWithTheIdentityOrderDoesNotWrite() {
        quickWizard.setData(entries(data1, data2))
        clearInvocations(preferences)

        assertThat(quickWizard.reorder(listOf(0, 1))).isTrue()

        assertThat(buttonTexts()).containsExactly("Meal", "Lunch").inOrder()
        // The entries ride a bidirectionally synced preference, so a pointless write would be a full
        // round trip to the paired device.
        verify(preferences, never()).put(eq(StringNonKey.QuickWizard), any<String>())
    }

    @Test
    fun reorderRejectsAnOrderThatIsNotAPermutation() {
        quickWizard.setData(entries(data1, data2, data3))

        assertThat(quickWizard.reorder(listOf(0, 1))).isFalse()          // too short
        assertThat(quickWizard.reorder(listOf(0, 1, 1))).isFalse()       // duplicate
        assertThat(quickWizard.reorder(listOf(0, 1, 5))).isFalse()       // out of range

        assertThat(buttonTexts()).containsExactly("Meal", "Lunch", "Dinner").inOrder()
    }

    // ---- doCalc: the dose holds the super bolus only when the SUPER_BOLUS mode can start ----

    // In the stored data 0 means ALWAYS, so this entry has the super bolus flag on.
    private val dataSuperBolus = "{\"buttonText\":\"Meal\",\"carbs\":36,\"validFrom\":0,\"validTo\":18000," +
        "\"useBG\":1,\"useCOB\":1,\"useBolusIOB\":1,\"useBasalIOB\":1,\"useTrend\":1,\"useSuperBolus\":0,\"useTemptarget\":1}"

    /** Runs the real `doCalc` of an entry with the super bolus flag and returns the flag it gave to the wizard. */
    private suspend fun superBolusGivenToTheWizard(preferenceOn: Boolean, runningMode: RM.Mode, allowedNextModes: List<RM.Mode>): Boolean {
        whenever(preferences.get(BooleanKey.OverviewUseSuperBolus)).thenReturn(preferenceOn)
        whenever(loop.runningMode()).thenReturn(runningMode)
        whenever(loop.allowedNextModes()).thenReturn(allowedNextModes)
        whenever(
            bolusWizard.doCalc(
                anyOrNull(), any(), anyOrNull(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyOrNull()
            )
        ).thenReturn(bolusWizard)
        quickWizard.setData(entries(dataSuperBolus))
        assertThat(quickWizard[0].useSuperBolus()).isEqualTo(QuickWizardEntry.ALWAYS)

        quickWizard[0].doCalc(mock<Profile>(), "Test", mock<InMemoryGlucoseValue>())

        val useSuperBolus = argumentCaptor<Boolean>()
        verify(bolusWizard).doCalc(
            anyOrNull(), any(), anyOrNull(), any(), any(), any(), any(), any(), any(), any(), any(), any(), useSuperBolus.capture(), any(), any(), any(), any(), any(),
            any(), any(), any(), anyOrNull()
        )
        return useSuperBolus.firstValue
    }

    @Test
    fun doCalc_superBolusFlagAndPreferenceAndModeAllowed_usesTheSuperBolus() = runTest {
        val used = superBolusGivenToTheWizard(preferenceOn = true, runningMode = RM.Mode.CLOSED_LOOP, allowedNextModes = listOf(RM.Mode.OPEN_LOOP, RM.Mode.SUPER_BOLUS))

        assertThat(used).isTrue()
    }

    @Test
    fun doCalc_superBolusPreferenceOff_leavesTheSuperBolusOut() = runTest {
        val used = superBolusGivenToTheWizard(preferenceOn = false, runningMode = RM.Mode.CLOSED_LOOP, allowedNextModes = listOf(RM.Mode.OPEN_LOOP, RM.Mode.SUPER_BOLUS))

        assertThat(used).isFalse()
    }

    @Test
    fun doCalc_superBolusModeAlreadyRuns_leavesTheSuperBolusOut() = runTest {
        val used = superBolusGivenToTheWizard(preferenceOn = true, runningMode = RM.Mode.SUPER_BOLUS, allowedNextModes = listOf(RM.Mode.DISCONNECTED_PUMP, RM.Mode.RESUME))

        assertThat(used).isFalse()
    }

    @Test
    fun doCalc_superBolusModeNotAllowedNow_leavesTheSuperBolusOut() = runTest {
        // The user suspended the loop: a bolus is allowed, but the SUPER_BOLUS mode cannot start from there.
        // The dose must not hold the 2 hours of basal, because nothing would stop the basal.
        val used = superBolusGivenToTheWizard(
            preferenceOn = true, runningMode = RM.Mode.SUSPENDED_BY_USER, allowedNextModes = listOf(RM.Mode.DISCONNECTED_PUMP, RM.Mode.SUSPENDED_BY_USER, RM.Mode.RESUME)
        )

        assertThat(used).isFalse()
    }
}
