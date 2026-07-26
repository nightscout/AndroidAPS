package app.aaps.implementation.queue

import android.content.Context
import android.os.PowerManager
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.implementation.profile.ProfileSwitchSilentGate
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import javax.inject.Provider
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class QueueWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var powerManager: PowerManager
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var localAlertUtils: LocalAlertUtils
    private val localAlertUtilsProvider: Provider<LocalAlertUtils> by lazy { Provider { localAlertUtils } }
    @Mock lateinit var smsCommunicator: SmsCommunicator
    private val smsCommunicatorProvider: Provider<SmsCommunicator> by lazy { Provider { smsCommunicator } }
    @Mock lateinit var jobName: CommandQueueName
    @Mock lateinit var workManager: WorkManager

    private val testScope = CoroutineScope(Dispatchers.Unconfined)
    private val bolusProgressData by lazy { BolusProgressData(ch, rh, testScope) }
    private val profileSwitchSilentGate = ProfileSwitchSilentGate()

    private lateinit var commandQueue: CommandQueueImplementation
    private lateinit var sut: QueueWorker

    @BeforeEach
    fun prepare() {
        whenever(persistenceLayer.observeChanges(anyOrNull<Class<*>>())).thenReturn(emptyFlow())
        commandQueue = CommandQueueImplementation(
            aapsLogger, rxBus, rh, constraintChecker,
            profileFunction, activePlugin, config, dateUtil, fabricPrivacy,
            notificationManager, persistenceLayer, decimalFormatter, pumpEnactResultProvider, pumpSync, preferences, profileSwitchSilentGate, localAlertUtilsProvider, smsCommunicatorProvider, jobName, workManager, testScope, bolusProgressData
        )

        val pumpDescription = PumpDescription()
        pumpDescription.basalMinimumRate = 0.1

        whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        runBlocking { whenever(profileFunction.getProfile()).thenReturn(effectiveProfile) }

        val bolusConstraint = ConstraintObject(0.0, aapsLogger)
        whenever(constraintChecker.applyBolusConstraints(anyOrNull())).thenReturn(bolusConstraint)
        whenever(constraintChecker.applyExtendedBolusConstraints(anyOrNull())).thenReturn(bolusConstraint)
        val carbsConstraint = ConstraintObject(0, aapsLogger)
        whenever(constraintChecker.applyCarbsConstraints(anyOrNull())).thenReturn(carbsConstraint)
        val rateConstraint = ConstraintObject(0.0, aapsLogger)
        whenever(constraintChecker.applyBasalConstraints(anyOrNull(), anyOrNull())).thenReturn(rateConstraint)
        val percentageConstraint = ConstraintObject(0, aapsLogger)
        whenever(constraintChecker.applyBasalPercentConstraints(anyOrNull(), anyOrNull()))
            .thenReturn(percentageConstraint)
        whenever(rh.gs(ArgumentMatchers.eq(app.aaps.core.ui.R.string.temp_basal_absolute), anyOrNull(), anyOrNull())).thenReturn("TEMP BASAL %1\$.2f U/h %2\$d min")

        // QueueWorker now uses constructor injection (@HiltWorker). Supply a WorkerFactory that
        // builds it with the test mocks instead of relying on field injection.
        sut = TestListenableWorkerBuilder<QueueWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    QueueWorker(
                        appContext, workerParameters, aapsLogger, fabricPrivacy, commandQueue,
                        rxBus, activePlugin, rh, preferences, config, bolusProgressData
                    )
            })
            .build()
    }

    @Test
    fun commandIsPickedUp() = runTest(timeout = 30.seconds) {
        val tbrJob = launch { commandQueue.tempBasalAbsolute(2.0, 60, true, validProfile, PumpSync.TemporaryBasalType.NORMAL) }
        yield()
        val result = sut.doWorkAndLog()
        tbrJob.join()
        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(commandQueue.size()).isEqualTo(0)
    }
}
