package info.nightscout.implementation.queue

import android.content.Context
import android.os.PowerManager
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.database.impl.AppRepository
import info.nightscout.implementation.queue.commands.CommandTempBasalAbsolute
import info.nightscout.interfaces.AndroidPermission
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.db.PersistenceLayer
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.queue.Command
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.sharedtests.TestBaseWithProfile
import info.nightscout.sharedtests.TestPumpPlugin
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito

class QueueThreadTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var powerManager: PowerManager
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var androidPermission: AndroidPermission
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Command) {
                it.aapsLogger = aapsLogger
                it.rh = rh
            }
            if (it is CommandTempBasalAbsolute) {
                it.activePlugin = activePlugin
                it.rh = rh
            }
        }
    }

    private lateinit var pumpPlugin: TestPumpPlugin
    private lateinit var commandQueue: CommandQueueImplementation
    private lateinit var sut: QueueThread

    @BeforeEach
    fun prepare() {
        pumpPlugin = TestPumpPlugin(injector)
        commandQueue = CommandQueueImplementation(
            injector, aapsLogger, rxBus, aapsSchedulers, rh, constraintChecker,
            profileFunction, activePlugin, context, sp,
            config, dateUtil, repository, fabricPrivacy, androidPermission, uiInteraction, persistenceLayer, decimalFormatter
        )

        val pumpDescription = PumpDescription()
        pumpDescription.basalMinimumRate = 0.1

        Mockito.`when`(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        Mockito.`when`(profileFunction.getProfile()).thenReturn(validProfile)

        val bolusConstraint = Constraint(0.0)
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(bolusConstraint)
        Mockito.`when`(constraintChecker.applyExtendedBolusConstraints(anyObject())).thenReturn(bolusConstraint)
        val carbsConstraint = Constraint(0)
        Mockito.`when`(constraintChecker.applyCarbsConstraints(anyObject())).thenReturn(carbsConstraint)
        val rateConstraint = Constraint(0.0)
        Mockito.`when`(constraintChecker.applyBasalConstraints(anyObject(), anyObject())).thenReturn(rateConstraint)
        val percentageConstraint = Constraint(0)
        Mockito.`when`(constraintChecker.applyBasalPercentConstraints(anyObject(), anyObject()))
            .thenReturn(percentageConstraint)
        Mockito.`when`(rh.gs(ArgumentMatchers.eq(info.nightscout.core.ui.R.string.temp_basal_absolute), anyObject(), anyObject())).thenReturn("TEMP BASAL %1\$.2f U/h %2\$d min")

        sut = QueueThread(commandQueue, context, aapsLogger, rxBus, activePlugin, rh, sp, androidPermission, config)
    }

    @Test
    fun commandIsPickedUp() {
        commandQueue.tempBasalAbsolute(2.0, 60, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        @Suppress("CallToThreadRun")
        sut.run()
        Assertions.assertEquals(0, commandQueue.size())
    }
}