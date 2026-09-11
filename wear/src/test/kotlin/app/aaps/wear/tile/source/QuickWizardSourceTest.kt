package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [QuickWizardSource].
 *
 * Time handling: [QuickWizardSource] derives "seconds from midnight" (sfm) from the real wall clock
 * ([System.currentTimeMillis] via [java.util.Calendar]) and is not injectable, so tests use all-day
 * windows (validFrom=0, validTo=86400) to make an entry deterministically active, and validTo=0
 * (or an empty guid) to make it deterministically inactive. [getValidFor] arithmetic depends on the
 * wall clock, so its numeric result is asserted by relationship/bounds, never a fixed constant.
 */
class QuickWizardSourceTest {

    private val aapsLogger = AAPSLoggerTest()
    private val sp: SP = mock()
    private val context: Context = mock()
    private val resources: Resources = mock()

    private lateinit var source: QuickWizardSource

    // Full day in seconds; sfm is always inside [0, 86400) so this window is always active.
    private val allDay = 24 * 60 * 60

    private val insulinSub = "1.5U"
    private val carbsSub = "20g"
    private val confirmation = "Really send?"

    @BeforeEach
    fun setup() {
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getString(eq(R.string.quick_wizard_tile_insulin), any())).thenReturn(insulinSub)
        whenever(resources.getString(eq(R.string.quick_wizard_tile_carbs), any())).thenReturn(carbsSub)
        whenever(resources.getString(R.string.action_quick_wizard_confirmation)).thenReturn(confirmation)
        source = QuickWizardSource(context, sp, aapsLogger)
    }

    private fun entry(
        guid: String = "guid-1",
        buttonText: String = "QW",
        carbs: Int = 20,
        validFrom: Int = 0,
        validTo: Int = allDay,
        mode: Int = 0,
        insulin: Double = 1.5
    ) = EventData.QuickWizard.QuickWizardEntry(
        guid = guid,
        buttonText = buttonText,
        carbs = carbs,
        validFrom = validFrom,
        validTo = validTo,
        lastUsed = 0L,
        mode = mode,
        insulin = insulin
    )

    /** Stub the sp data string with a serialized [EventData.QuickWizard] of the given entries. */
    private fun stubQuickWizard(vararg entries: EventData.QuickWizard.QuickWizardEntry) {
        val serialized = EventData.QuickWizard(arrayListOf(*entries)).serialize()
        whenever(sp.getString(eq(R.string.key_quick_wizard_data), any())).thenReturn(serialized)
    }

    @Test
    fun getSelectedActionsReturnsEmptyWhenNoEntries() {
        stubQuickWizard()
        assertThat(source.getSelectedActions()).isEmpty()
    }

    @Test
    fun getSelectedActionsSkipsEntryWithEmptyGuid() {
        // Active window but empty guid -> filtered out.
        stubQuickWizard(entry(guid = "", validFrom = 0, validTo = allDay))
        assertThat(source.getSelectedActions()).isEmpty()
    }

    @Test
    fun getSelectedActionsSkipsEntryOutsideWindow() {
        // validTo=0 means sfm (always > 0 after midnight) is outside 0..0 -> filtered out.
        stubQuickWizard(entry(guid = "guid-x", validFrom = 0, validTo = 0))
        assertThat(source.getSelectedActions()).isEmpty()
    }

    @Test
    fun getSelectedActionsInsulinModeUsesBolusIconAndInsulinSubtext() {
        stubQuickWizard(entry(guid = "guid-i", buttonText = "Bolus", mode = 1, insulin = 1.5))
        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(1)
        val action = actions[0]
        assertThat(action.buttonText).isEqualTo("Bolus")
        assertThat(action.buttonTextSub).isEqualTo(insulinSub)
        assertThat(action.iconRes).isEqualTo(R.drawable.ic_bolus)
        assertThat(action.activityClass).isEqualTo(BackgroundActionActivity::class.java.name)
        assertThat(action.message).isEqualTo(confirmation)
        assertThat(action.action).isEqualTo(EventData.ActionQuickWizardPreCheck("guid-i"))
    }

    @Test
    fun getSelectedActionsCarbsModeUsesCarbsIconAndCarbsSubtext() {
        stubQuickWizard(entry(guid = "guid-c", buttonText = "Carbs", mode = 2, carbs = 20))
        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(1)
        val action = actions[0]
        assertThat(action.buttonTextSub).isEqualTo(carbsSub)
        assertThat(action.iconRes).isEqualTo(R.drawable.ic_carbs_orange)
        assertThat(action.action).isEqualTo(EventData.ActionQuickWizardPreCheck("guid-c"))
    }

    @Test
    fun getSelectedActionsWizardModeUsesQuickWizardIconAndCarbsSubtext() {
        // mode 0 (WIZARD) -> generic quick wizard icon, carbs subtext branch.
        stubQuickWizard(entry(guid = "guid-w", buttonText = "Wizard", mode = 0, carbs = 20))
        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(1)
        val action = actions[0]
        assertThat(action.buttonTextSub).isEqualTo(carbsSub)
        assertThat(action.iconRes).isEqualTo(R.drawable.ic_quick_wizard)
    }

    @Test
    fun getSelectedActionsReturnsOnlyActiveEntries() {
        stubQuickWizard(
            entry(guid = "active-1", buttonText = "A1", mode = 1, validFrom = 0, validTo = allDay),
            entry(guid = "inactive", buttonText = "IN", mode = 2, validFrom = 0, validTo = 0),
            entry(guid = "", buttonText = "NoGuid", mode = 2, validFrom = 0, validTo = allDay),
            entry(guid = "active-2", buttonText = "A2", mode = 2, validFrom = 0, validTo = allDay)
        )
        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(2)
        assertThat(actions.map { it.buttonText }).containsExactly("A1", "A2")
    }

    @Test
    fun getValidForNullWhenNoEntries() {
        stubQuickWizard()
        assertThat(source.getValidFor()).isNull()
    }

    @Test
    fun getValidForPositiveAndBoundedForActiveAllDayEntry() {
        // An active all-day entry: validTill stays 86400, so result = (86400 - sfm + 60) * 1000.
        // sfm is in [0, 86400), so result is strictly positive and at most (86400 + 60) * 1000.
        stubQuickWizard(entry(guid = "guid-a", validFrom = 0, validTo = allDay))

        val validFor = source.getValidFor()
        assertThat(validFor).isNotNull()
        val upperBound = (allDay + 60) * 1000L
        assertThat(validFor!!).isGreaterThan(0L)
        assertThat(validFor).isAtMost(upperBound)
    }

    @Test
    fun getValidForIgnoresEmptyGuidEntriesButStaysNonNull() {
        // Non-empty list -> not null. Empty-guid entry never narrows validTill; default 86400 applies.
        stubQuickWizard(entry(guid = "", validFrom = 100, validTo = 200))

        val validFor = source.getValidFor()
        assertThat(validFor).isNotNull()
        val upperBound = (allDay + 60) * 1000L
        assertThat(validFor!!).isGreaterThan(0L)
        assertThat(validFor).isAtMost(upperBound)
    }

    @Test
    fun getResourceReferencesReturnsThreeFixedDrawables() {
        val refs = source.getResourceReferences(resources)
        assertThat(refs).containsExactly(
            R.drawable.ic_quick_wizard,
            R.drawable.ic_bolus,
            R.drawable.ic_carbs_orange
        )
    }
}
