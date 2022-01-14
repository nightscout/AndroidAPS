package info.nightscout.androidaps.queue

import android.content.Context
import android.os.PowerManager
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.queue.commands.CommandTempBasalAbsolute
import info.nightscout.androidaps.utils.buildHelper.BuildHelperImpl
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito

class QueueThreadTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var sp: SP
    @Mock lateinit var fileListProvider: PrefFileListProvider
    @Mock lateinit var powerManager: PowerManager
    @Mock lateinit var repository: AppRepository

    val injector = HasAndroidInjector {
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

    @Before
    fun prepare() {
        pumpPlugin = TestPumpPlugin(injector)
        commandQueue = CommandQueueImplementation(
            injector, aapsLogger, rxBus, aapsSchedulers, rh, constraintChecker,
            profileFunction, activePlugin, context, sp,
            BuildHelperImpl(config, fileListProvider), dateUtil, repository, fabricPrivacy, config
        )

        val pumpDescription = PumpDescription()
        pumpDescription.basalMinimumRate = 0.1

        Mockito.`when`(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        Mockito.`when`(activePlugin.activePump).thenReturn(pumpPlugin)
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
        Mockito.`when`(rh.gs(ArgumentMatchers.eq(R.string.temp_basal_absolute), anyObject(), anyObject())).thenReturn("TEMP BASAL %1\$.2f U/h %2\$d min")

        sut = QueueThread(commandQueue, context, aapsLogger, rxBus, activePlugin, rh, sp)
    }

    @Test
    fun commandIsPickedUp() {
        commandQueue.tempBasalAbsolute(2.0, 60, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        sut.run()
        Assert.assertEquals(0, commandQueue.size())
    }
}