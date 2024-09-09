package app.aaps.implementation.queue

import android.content.Context
import android.os.PowerManager
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.implementation.queue.commands.CommandTempBasalAbsolute
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito

class QueueThreadTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var powerManager: PowerManager
    @Mock lateinit var androidPermission: AndroidPermission
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var persistenceLayer: PersistenceLayer

    init {
        addInjector {
            if (it is CommandTempBasalAbsolute) {
                it.aapsLogger = aapsLogger
                it.activePlugin = activePlugin
                it.rh = rh
            }
        }
    }

    private lateinit var commandQueue: CommandQueueImplementation
    private lateinit var sut: QueueThread

    @BeforeEach
    fun prepare() {
        commandQueue = CommandQueueImplementation(
            injector, aapsLogger, rxBus, aapsSchedulers, rh, constraintChecker,
            profileFunction, activePlugin, context, sp, preferences, config, dateUtil, fabricPrivacy, androidPermission,
            uiInteraction, persistenceLayer, decimalFormatter, instantiator
        )

        val pumpDescription = PumpDescription()
        pumpDescription.basalMinimumRate = 0.1

        Mockito.`when`(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        Mockito.`when`(profileFunction.getProfile()).thenReturn(validProfile)

        val bolusConstraint = ConstraintObject(0.0, aapsLogger)
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(bolusConstraint)
        Mockito.`when`(constraintChecker.applyExtendedBolusConstraints(anyObject())).thenReturn(bolusConstraint)
        val carbsConstraint = ConstraintObject(0, aapsLogger)
        Mockito.`when`(constraintChecker.applyCarbsConstraints(anyObject())).thenReturn(carbsConstraint)
        val rateConstraint = ConstraintObject(0.0, aapsLogger)
        Mockito.`when`(constraintChecker.applyBasalConstraints(anyObject(), anyObject())).thenReturn(rateConstraint)
        val percentageConstraint = ConstraintObject(0, aapsLogger)
        Mockito.`when`(constraintChecker.applyBasalPercentConstraints(anyObject(), anyObject()))
            .thenReturn(percentageConstraint)
        Mockito.`when`(rh.gs(ArgumentMatchers.eq(app.aaps.core.ui.R.string.temp_basal_absolute), anyObject(), anyObject())).thenReturn("TEMP BASAL %1\$.2f U/h %2\$d min")

        sut = QueueThread(commandQueue, context, aapsLogger, rxBus, activePlugin, rh, sp, preferences, androidPermission, config)
    }

    @Test
    fun commandIsPickedUp() {
        commandQueue.tempBasalAbsolute(2.0, 60, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        @Suppress("CallToThreadRun")
        sut.run()
        assertThat(commandQueue.size()).isEqualTo(0)
    }
}
