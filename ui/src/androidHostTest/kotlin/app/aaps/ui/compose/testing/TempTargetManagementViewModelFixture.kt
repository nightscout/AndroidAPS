package app.aaps.ui.compose.testing

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.tempTargets.toJson
import app.aaps.core.keys.StringNonKey
import app.aaps.ui.compose.tempTarget.TempTargetManagementViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Builds a real [TempTargetManagementViewModel] with its presets already loaded.
 *
 * The view model is the real one, not a mock - the screen calls back into it for
 * `hasUnsavedChanges()`, `canReorder()` and `isEditorDifferentFromDefaults()`, and those decide what
 * the toolbar shows. Only its dependencies are mocked.
 *
 * Shares [AapsScreenFixture.preferences], `dateUtil` and `profileUtil` with the screen environment,
 * so what the view model reads and what the composables read cannot drift apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TempTargetManagementViewModelFixture(private val screen: AapsScreenFixture) {

    val persistenceLayer: PersistenceLayer = mock()
    val profileFunction: ProfileFunction = mock()
    val rh: ResourceHelper = mock()
    val aapsLogger: AAPSLogger = mock()
    val rxBus: RxBus = mock()
    val batchExecutor: BatchExecutor = mock()
    val config = screen.config

    init {
        stubResourcesFromRobolectric(rh)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        // mg/dL storage and mg/dL display, so a target reads back as the number the test wrote.
        whenever(screen.profileUtil.fromMgdlToUnits(any(), any())).thenAnswer { it.arguments[0] as Double }
        whenever(screen.profileUtil.convertToMgdl(any(), any())).thenAnswer { it.arguments[0] as Double }
        whenever(screen.profileUtil.fromMgdlToStringInUnits(anyOrNull(), anyOrNull())).thenReturn("100 mg/dL")
        whenever(screen.profileUtil.units).thenReturn(GlucoseUnit.MGDL)
        whenever(screen.dateUtil.now()).thenReturn(NOW)
        // The editor puts these straight into text fields, and a mock's null reaches Compose as a
        // NullPointerException inside BasicTextField - nowhere near the missing stub.
        whenever(screen.dateUtil.dateString(any())).thenReturn("2023-11-14")
        whenever(screen.dateUtil.timeString(any())).thenReturn("21:33")
        whenever(persistenceLayer.observeChanges(TT::class)).thenReturn(emptyFlow())
        whenever(screen.preferences.observe(StringNonKey.TempTargetPresets)).thenReturn(MutableStateFlow("[]"))
        whenever(screen.preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")
    }

    /**
     * @param presets the stored preset list, written as the JSON the view model really parses.
     * @param activeTT the temp target running right now, or null.
     */
    fun build(presets: List<TTPreset> = emptyList(), activeTT: TT? = null): TempTargetManagementViewModel {
        whenever(screen.preferences.get(StringNonKey.TempTargetPresets)).thenReturn(presets.toJson())
        runBlocking { whenever(persistenceLayer.getTemporaryTargetActiveAt(NOW)).thenReturn(activeTT) }
        // Unconfined so the init{} loadData() has finished by the time the screen is set - otherwise
        // the screen renders the loading spinner and every assertion misses.
        return TempTargetManagementViewModel(
            persistenceLayer, profileFunction, screen.profileUtil, screen.preferences, rh,
            screen.dateUtil, aapsLogger, rxBus, batchExecutor, config,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    companion object {

        const val NOW = 1_700_000_000_000L
    }
}
