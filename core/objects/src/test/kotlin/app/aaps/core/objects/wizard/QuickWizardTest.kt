package app.aaps.core.objects.wizard

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.keys.StringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.inject.Provider

class QuickWizardTest : TestBaseWithProfile() {

    @Mock lateinit var loop: Loop
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var bolusWizardProvider: Provider<BolusWizard>
    @Mock lateinit var quickWizardProvider: Provider<QuickWizard>

    private val data1 = "{\"buttonText\":\"Meal\",\"carbs\":36,\"validFrom\":0,\"validTo\":18000," +
        "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":0,\"useBasalIOB\":0,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}"
    private val data2 = "{\"buttonText\":\"Lunch\",\"carbs\":18,\"validFrom\":36000,\"validTo\":39600," +
        "\"useBG\":0,\"useCOB\":0,\"useBolusIOB\":1,\"useBasalIOB\":2,\"useTrend\":0,\"useSuperBolus\":0,\"useTemptarget\":0}"
    private var array: JSONArray = JSONArray("[$data1,$data2]")

    class MockedTime : QuickWizardEntry.Time() {

        override fun secondsFromMidnight() = 0
    }

    private val mockedTime = MockedTime()
    private lateinit var quickWizard: QuickWizard

    @BeforeEach
    fun setup() {
        whenever(preferences.get(StringNonKey.QuickWizard)).thenReturn("[]")
        whenever(preferences.observe(StringNonKey.QuickWizard)).thenReturn(MutableStateFlow("[]"))
        val quickWizardEntry = QuickWizardEntry(aapsLogger, preferences, profileFunction, loop, iobCobCalculator, persistenceLayer, dateUtil, glucoseStatusProvider, bolusWizardProvider, quickWizardProvider)
        quickWizardEntry.time = mockedTime
        val quickWizardEntryProvider = Provider { quickWizardEntry }
        quickWizard = QuickWizard(preferences, quickWizardEntryProvider)
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
        quickWizard.setData(JSONArray("[$data1,$data2,$data3]"))

        // order[newIndex] == oldIndex, so this reads "Dinner, Meal, Lunch".
        assertThat(quickWizard.reorder(listOf(2, 0, 1))).isTrue()

        assertThat(buttonTexts()).containsExactly("Dinner", "Meal", "Lunch").inOrder()
    }

    @Test
    fun reorderWithTheIdentityOrderDoesNotWrite() {
        quickWizard.setData(JSONArray("[$data1,$data2]"))
        clearInvocations(preferences)

        assertThat(quickWizard.reorder(listOf(0, 1))).isTrue()

        assertThat(buttonTexts()).containsExactly("Meal", "Lunch").inOrder()
        // The entries ride a bidirectionally synced preference, so a pointless write would be a full
        // round trip to the paired device.
        verify(preferences, never()).put(eq(StringNonKey.QuickWizard), any<String>())
    }

    @Test
    fun reorderRejectsAnOrderThatIsNotAPermutation() {
        quickWizard.setData(JSONArray("[$data1,$data2,$data3]"))

        assertThat(quickWizard.reorder(listOf(0, 1))).isFalse()          // too short
        assertThat(quickWizard.reorder(listOf(0, 1, 1))).isFalse()       // duplicate
        assertThat(quickWizard.reorder(listOf(0, 1, 5))).isFalse()       // out of range

        assertThat(buttonTexts()).containsExactly("Meal", "Lunch", "Dinner").inOrder()
    }
}
