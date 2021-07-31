package info.nightscout.androidaps.queue

import android.content.Context
import android.os.PowerManager
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.queue.commands.CommandTempBasalAbsolute
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.buildHelper.ConfigImpl
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(
    ConstraintChecker::class, VirtualPumpPlugin::class, ToastUtils::class, Context::class,
    FabricPrivacy::class, PrefFileListProvider::class, PowerManager::class)
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
                it.resourceHelper = resourceHelper
            }
            if (it is CommandTempBasalAbsolute) {
                it.activePlugin = activePlugin
            }
        }
    }

    private lateinit var pumpPlugin: TestPumpPlugin
    private lateinit var commandQueue: CommandQueue
    private lateinit var sut: QueueThread

    @Before
    fun prepare() {
        pumpPlugin = TestPumpPlugin(injector)
        commandQueue = CommandQueue(injector, aapsLogger, rxBus, aapsSchedulers, resourceHelper, constraintChecker, profileFunction, activePlugin, context, sp, BuildHelper(ConfigImpl(), fileListProvider), dateUtil, repository, fabricPrivacy)

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
        Mockito.`when`(constraintChecker.applyBasalPercentConstraints(anyObject(), anyObject())).thenReturn(percentageConstraint)

        sut = QueueThread(commandQueue, context, aapsLogger, rxBus, activePlugin, resourceHelper, sp)
    }

    @Test
    fun commandIsPickedUp() {
        commandQueue.tempBasalAbsolute(2.0, 60, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        sut.run()
        Assert.assertEquals(0, commandQueue.size())
    }
}