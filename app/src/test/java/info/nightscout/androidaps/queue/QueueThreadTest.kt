package info.nightscout.androidaps.queue

import android.content.Context
import android.os.PowerManager
import dagger.Lazy
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.maintenance.LoggerUtils
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.queue.commands.CommandTempBasalAbsolute
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest(
    ConstraintChecker::class, VirtualPumpPlugin::class, ToastUtils::class, Context::class,
    TreatmentsPlugin::class, FabricPrivacy::class, LoggerUtils::class, PowerManager::class)
class QueueThreadTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var lazyActivePlugin: Lazy<ActivePluginProvider>
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var loggerUtils: LoggerUtils
    @Mock lateinit var powerManager: PowerManager

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
    lateinit var commandQueue: CommandQueue
    lateinit var sut: QueueThread

    @Before
    fun prepare() {
        pumpPlugin = TestPumpPlugin(injector)
        commandQueue = CommandQueue(injector, aapsLogger, rxBus, aapsSchedulers, resourceHelper, constraintChecker, profileFunction, lazyActivePlugin, context, sp, BuildHelper(Config(), loggerUtils), fabricPrivacy)

        val pumpDescription = PumpDescription()
        pumpDescription.basalMinimumRate = 0.1

        Mockito.`when`(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        Mockito.`when`(lazyActivePlugin.get()).thenReturn(activePlugin)
        Mockito.`when`(activePlugin.activePump).thenReturn(pumpPlugin)
        Mockito.`when`(activePlugin.activeTreatments).thenReturn(treatmentsPlugin)
        Mockito.`when`(treatmentsPlugin.lastBolusTime).thenReturn(Calendar.getInstance().also { it.set(2000, 0, 1) }.timeInMillis)
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
        commandQueue.tempBasalAbsolute(2.0, 60, true, validProfile, null)
        sut.run()
        Assert.assertEquals(0, commandQueue.size())
    }
}