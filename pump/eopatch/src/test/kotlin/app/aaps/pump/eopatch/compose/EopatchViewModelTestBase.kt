package app.aaps.pump.eopatch.compose

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.RxAction
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import app.aaps.pump.eopatch.vo.PatchState
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The fourteen dependencies [EopatchPatchViewModel] takes, and the stubs its `init` block needs.
 *
 * Building this view model is not free: `init` subscribes to the patch life cycle, reads the patch
 * state, and starts a coroutine on `viewModelScope`, so a bare set of mocks hands back nulls and the
 * Rx chains die on them. Everything needed to get past construction lives here once, so a test can
 * be about the behaviour it is checking instead of about assembling the object.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class EopatchViewModelTestBase {

    protected val rh: ResourceHelper = mock()
    protected val patchManager: IPatchManager = mock()
    protected val patchManagerExecutor: PatchManagerExecutor = mock()
    protected val preferenceManager: PreferenceManager = mock()
    protected val patchConfig: PatchConfig = mock()
    protected val alarmRegistry: IAlarmRegistry = mock()
    protected val aapsLogger: AAPSLogger = mock()
    protected val aapsSchedulers: AapsSchedulers = mock()
    protected val rxAction: RxAction = mock()
    protected val preferences: Preferences = mock()
    protected val insulinManager: InsulinManager = mock()
    protected val profileFunction: ProfileFunction = mock()
    protected val profileRepository: ProfileRepository = mock()
    protected val persistenceLayer: PersistenceLayer = mock()

    protected val testDispatcher = StandardTestDispatcher()

    /**
     * Deliberately not annotated. The plain view model tests run on JUnit 5 while the Compose ones
     * run on JUnit 4 under Robolectric, and a `@BeforeEach` here is simply ignored by the JUnit 4
     * runner - silently, leaving every stub unset. Each subclass wires this to its own runner's
     * before-hook instead.
     */
    protected fun setUpMocks() = runTest(testDispatcher) {
        // viewModelScope runs on Main, which a unit test has to provide.
        Dispatchers.setMain(testDispatcher)
        // All four schedulers on the trampoline, so Rx work runs inline rather than on another thread.
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.cpu).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.newThread).thenReturn(Schedulers.trampoline())
        whenever(preferenceManager.observePatchLifeCycle()).thenReturn(Observable.never())
        whenever(preferenceManager.patchState).thenReturn(PatchState())
        whenever(patchConfig.lifecycleEvent).thenReturn(PatchLifecycleEvent.createShutdown())
        // Alarm handling rides on Maybe, and add() has a defaulted third parameter, so all three
        // arguments have to be matched.
        whenever(alarmRegistry.remove(any())).thenReturn(Maybe.empty())
        whenever(alarmRegistry.add(any(), any(), any())).thenReturn(Maybe.empty())
        // Arriving at WAKE_UP starts looking for a patch; never() leaves that scan pending.
        whenever(patchManager.scan(any())).thenReturn(Single.never())
        whenever(persistenceLayer.getTherapyEventDataFromTime(any(), any<Boolean>())).thenReturn(emptyList())
    }

    protected fun tearDownMocks() {
        Dispatchers.resetMain()
    }

    protected fun sut() = EopatchPatchViewModel(
        rh, patchManager, patchManagerExecutor, preferenceManager, patchConfig, alarmRegistry,
        aapsLogger, aapsSchedulers, rxAction, preferences, insulinManager, profileFunction,
        profileRepository, persistenceLayer
    )
}
