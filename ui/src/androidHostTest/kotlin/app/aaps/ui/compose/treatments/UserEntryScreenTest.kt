package app.aaps.ui.compose.treatments

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.ui.R
import app.aaps.ui.UiStringIds
import app.aaps.ui.compose.treatments.viewmodels.UserEntryViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric composable test for [UserEntryScreen].
 *
 * The history list is the record a user checks after something unexpected happened, so what it
 * renders matters: an entry's time, the translated action, the values and the note, one header per
 * day, and the "no records" message when a filter leaves nothing. It also pins the one place the
 * screen composes text itself - a TREATMENT entry is shown as bolus + carbs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UserEntryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val persistenceLayer: PersistenceLayer = mock()
    private val rh: ResourceHelper = mock()
    private val dateUtil: DateUtil = mock()
    private val aapsLogger: AAPSLogger = mock()
    private val rxBus: RxBus = mock()
    private val preferences: Preferences = mock()

    private val presentationHelper: UserEntryPresentationHelper = mock()
    private val translator: Translator = mock()
    private val importExportPrefs: ImportExportPrefs = mock()
    private val uel: UserEntryLogger = mock()

    private lateinit var ctx: Context
    private lateinit var noRecords: String

    private val now = 1_700_000_000_000L
    private val yesterday = now - 24 * 60 * 60_000L
    private val icon: ImageVector = Icons.Default.Add

    @Before
    fun setUp() {
        // MainApp does this in production; without it a TextRef.Named renders as its raw name.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        ctx = RuntimeEnvironment.getApplication()
        noRecords = ctx.getString(R.string.no_records_available)
        whenever(persistenceLayer.observeChanges(UE::class)).thenReturn(emptyFlow())
        whenever(dateUtil.now()).thenReturn(now)
        whenever(dateUtil.dateString(now)).thenReturn("2023-11-14")
        whenever(dateUtil.dateString(yesterday)).thenReturn("2023-11-13")
        whenever(dateUtil.dateStringRelative(eq(now), any())).thenReturn("Today")
        whenever(dateUtil.dateStringRelative(eq(yesterday), any())).thenReturn("Yesterday")
        whenever(dateUtil.timeStringWithSeconds(any())).thenReturn("21:33:20")
        whenever(presentationHelper.icon(any())).thenReturn(icon)
        whenever(presentationHelper.listToPresentationString(any())).thenReturn("")
        whenever(translator.translate(any<Action>())).thenReturn("Bolus")
        whenever(rh.gs(any<TextRef>())).thenReturn("Export to CSV")
        // AapsTheme reads the dark mode preference; without it LocalPreferences errors out.
        whenever(preferences.observe(StringKey.GeneralDarkMode)).thenReturn(MutableStateFlow("light"))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun entry(
        id: Long = 1L,
        timestamp: Long = now,
        action: Action = Action.BOLUS,
        note: String = "",
        values: List<ValueWithUnit> = emptyList()
    ) = UE(
        id = id,
        timestamp = timestamp,
        action = action,
        source = Sources.Treatments,
        note = note,
        values = values
    )

    /** Builds the view model with the entries already loaded - Unconfined runs its init eagerly. */
    private fun loadedViewModel(entries: List<UE>): UserEntryViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking {
            whenever(persistenceLayer.getUserEntryFilteredDataFromTime(any())).thenReturn(entries)
        }
        return UserEntryViewModel(persistenceLayer, rh, dateUtil, aapsLogger, rxBus)
    }

    private fun setScreen(viewModel: UserEntryViewModel, onToolbar: (ToolbarConfig) -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalDateUtil provides dateUtil
            ) {
                UserEntryScreen(
                    viewModel = viewModel,
                    userEntryPresentationHelper = presentationHelper,
                    translator = translator,
                    importExportPrefs = importExportPrefs,
                    uel = uel,
                    setToolbarConfig = onToolbar
                )
            }
        }
    }

    @Test
    fun showsNoRecordsMessageWhenTheListIsEmpty() {
        setScreen(loadedViewModel(emptyList()))

        compose.onNodeWithText(noRecords).assertIsDisplayed()
    }

    @Test
    fun showsNoRecordsMessageOnlyOnceTheLoadFinished() {
        // StandardTestDispatcher leaves the init{} load unrun, so the state stays on isLoading and
        // the spinner branch is what renders.
        Dispatchers.setMain(StandardTestDispatcher())
        setScreen(UserEntryViewModel(persistenceLayer, rh, dateUtil, aapsLogger, rxBus))

        compose.onNodeWithText(noRecords).assertDoesNotExist()
    }

    @Test
    fun showsTheDayHeaderTheTimeAndTheTranslatedAction() {
        whenever(translator.translate(Action.BOLUS)).thenReturn("Bolus")

        setScreen(loadedViewModel(listOf(entry(action = Action.BOLUS))))

        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("21:33:20").assertIsDisplayed()
        compose.onNodeWithText("Bolus").assertIsDisplayed()
        compose.onNodeWithText(noRecords).assertDoesNotExist()
    }

    @Test
    fun aTreatmentEntryIsShownAsBolusPlusCarbs() {
        whenever(translator.translate(Action.BOLUS)).thenReturn("Bolus")
        whenever(translator.translate(Action.CARBS)).thenReturn("Carbs")

        setScreen(loadedViewModel(listOf(entry(action = Action.TREATMENT))))

        compose.onNodeWithText("Bolus + Carbs").assertIsDisplayed()
    }

    @Test
    fun showsTheValuesAndTheNoteWhenAnEntryHasThem() {
        whenever(presentationHelper.listToPresentationString(any())).thenReturn("1.5 U")

        setScreen(loadedViewModel(listOf(entry(note = "before lunch"))))

        compose.onNodeWithText("1.5 U").assertIsDisplayed()
        compose.onNodeWithText("before lunch").assertIsDisplayed()
    }

    @Test
    fun groupsEntriesFromDifferentDaysUnderTheirOwnHeaders() {
        setScreen(
            loadedViewModel(
                listOf(entry(id = 1L), entry(id = 2L, timestamp = yesterday))
            )
        )

        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Yesterday").assertIsDisplayed()
    }

    @Test
    fun handsTheHostAToolbarSoTheScreenIsNotLeftWithoutOne() {
        var config: ToolbarConfig? = null

        setScreen(loadedViewModel(emptyList()), onToolbar = { config = it })
        compose.waitForIdle()

        assertThat(config).isNotNull()
    }
}
