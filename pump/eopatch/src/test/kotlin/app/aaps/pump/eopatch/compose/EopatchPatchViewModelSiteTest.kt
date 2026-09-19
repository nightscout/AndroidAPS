package app.aaps.pump.eopatch.compose

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.siteRotation.BodyType
import app.aaps.pump.eopatch.RxAction
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import app.aaps.pump.eopatch.vo.PatchState
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the site location part of [EopatchPatchViewModel] - where on the body the patch went.
 *
 * The choice is written onto the cannula-change therapy event so the next activation can suggest a
 * different spot. Two things must hold: skipping the step has to clear whatever was chosen before,
 * or the record would claim a site the user never picked, and a choice with nothing to attach it to
 * must not fail the activation - the site is optional, the patch is not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EopatchPatchViewModelSiteTest {

    private val rh: ResourceHelper = mock()
    private val patchManager: IPatchManager = mock()
    private val patchManagerExecutor: PatchManagerExecutor = mock()
    private val preferenceManager: PreferenceManager = mock()
    private val patchConfig: PatchConfig = mock()
    private val alarmRegistry: IAlarmRegistry = mock()
    private val aapsLogger: AAPSLogger = mock()
    private val aapsSchedulers: AapsSchedulers = mock()
    private val rxAction: RxAction = mock()
    private val preferences: Preferences = mock()
    private val insulinManager: InsulinManager = mock()
    private val profileFunction: ProfileFunction = mock()
    private val profileRepository: ProfileRepository = mock()
    private val persistenceLayer: PersistenceLayer = mock()

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.cpu).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.newThread).thenReturn(Schedulers.trampoline())
        whenever(preferenceManager.observePatchLifeCycle()).thenReturn(Observable.never())
        whenever(preferenceManager.patchState).thenReturn(PatchState())
        whenever(patchConfig.lifecycleEvent).thenReturn(PatchLifecycleEvent.createShutdown())
        whenever(alarmRegistry.remove(any())).thenReturn(Maybe.empty())
        whenever(alarmRegistry.add(any(), any(), any())).thenReturn(Maybe.empty())
        whenever(patchManager.scan(any())).thenReturn(Single.never())
        whenever(persistenceLayer.getTherapyEventDataFromTime(any(), any<Boolean>())).thenReturn(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sut() = EopatchPatchViewModel(
        rh, patchManager, patchManagerExecutor, preferenceManager, patchConfig, alarmRegistry,
        aapsLogger, aapsSchedulers, rxAction, preferences, insulinManager, profileFunction,
        profileRepository, persistenceLayer
    )

    // ---- choosing a site ----

    @Test
    fun aChosenSiteAndArrowAreHeld() {
        val viewModel = sut()

        viewModel.updateSiteLocation(TE.Location.FRONT_LEFT_UPPER_ABDOMEN)
        viewModel.updateSiteArrow(TE.Arrow.UP)

        assertThat(viewModel.siteLocation.value).isEqualTo(TE.Location.FRONT_LEFT_UPPER_ABDOMEN)
        assertThat(viewModel.siteArrow.value).isEqualTo(TE.Arrow.UP)
    }

    @Test
    fun completingTheSiteStepKeepsTheChoiceAndMovesOn() {
        val viewModel = sut()
        viewModel.updateSiteLocation(TE.Location.SIDE_RIGHT_UPPER_ARM)

        viewModel.completeSiteLocation()

        assertThat(viewModel.siteLocation.value).isEqualTo(TE.Location.SIDE_RIGHT_UPPER_ARM)
        assertThat(viewModel.patchStep.value).isEqualTo(PatchStep.REMOVE_PROTECTION_TAPE)
    }

    /** Skipping must forget an earlier choice, or the patch would be recorded at a site nobody picked. */
    @Test
    fun skippingTheSiteStepForgetsWhatWasChosen() {
        val viewModel = sut()
        viewModel.updateSiteLocation(TE.Location.FRONT_LEFT_UPPER_ABDOMEN)
        viewModel.updateSiteArrow(TE.Arrow.DOWN)

        viewModel.skipSiteLocation()

        assertThat(viewModel.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(viewModel.siteArrow.value).isEqualTo(TE.Arrow.NONE)
        assertThat(viewModel.patchStep.value).isEqualTo(PatchStep.REMOVE_PROTECTION_TAPE)
    }

    @Test
    fun theBodyTypeComesFromThePreference() {
        whenever(preferences.get(IntKey.SiteRotationUserProfile)).thenReturn(BodyType.WOMAN.value)

        assertThat(sut().bodyType()).isEqualTo(BodyType.WOMAN)
    }

    // ---- writing the site onto the therapy event ----

    @Test
    fun theChosenSiteIsWrittenOntoTheCannulaChange() = runTest(testDispatcher) {
        val activation = 1_700_000_000_000L
        val event = TE(timestamp = activation, type = TE.Type.CANNULA_CHANGE, glucoseUnit = GlucoseUnit.MGDL)
        whenever(persistenceLayer.getTherapyEventDataFromToTime(eq(activation), eq(activation))).thenReturn(listOf(event))
        val viewModel = sut()
        viewModel.updateSiteLocation(TE.Location.FRONT_LEFT_UPPER_ABDOMEN)
        viewModel.updateSiteArrow(TE.Arrow.UP)

        viewModel.saveSiteLocationToTherapyEvent(activation)
        advanceUntilIdle()

        val captor = argumentCaptor<TE>()
        verify(persistenceLayer).insertOrUpdateTherapyEvent(captor.capture())
        assertThat(captor.firstValue.location).isEqualTo(TE.Location.FRONT_LEFT_UPPER_ABDOMEN)
        assertThat(captor.firstValue.arrow).isEqualTo(TE.Arrow.UP)
    }

    /** Nothing chosen means nothing to record - the event is left as it is. */
    @Test
    fun withNoSiteChosenNothingIsWritten() = runTest(testDispatcher) {
        val viewModel = sut()

        viewModel.saveSiteLocationToTherapyEvent(1_700_000_000_000L)
        advanceUntilIdle()

        verify(persistenceLayer, never()).insertOrUpdateTherapyEvent(any())
    }

    /** The site is optional: a missing cannula-change event must not take the activation down. */
    @Test
    fun aMissingCannulaChangeIsNotAnError() = runTest(testDispatcher) {
        val activation = 1_700_000_000_000L
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(emptyList())
        val viewModel = sut()
        viewModel.updateSiteLocation(TE.Location.FRONT_LEFT_UPPER_ABDOMEN)

        viewModel.saveSiteLocationToTherapyEvent(activation)
        advanceUntilIdle()

        verify(persistenceLayer, never()).insertOrUpdateTherapyEvent(any())
    }
}
