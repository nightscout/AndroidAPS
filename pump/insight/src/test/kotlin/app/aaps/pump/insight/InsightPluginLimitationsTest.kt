package app.aaps.pump.insight

import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.ReadParameterBlockMessage
import app.aaps.pump.insight.app_layer.parameter_blocks.FactoryMinBasalAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.FactoryMinBolusAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.MaxBasalAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.MaxBolusAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.ParameterBlock
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.insight.connection_service.MessageRequest
import app.aaps.pump.insight.database.InsightDatabase
import app.aaps.pump.insight.database.InsightDbHelper
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Covers [InsightPlugin.fetchLimitations]: the bolus limits go to the bolus constraints and the basal
 * limits go to the description. The bolus limits used to go to the description too, so the profile
 * check used the max bolus as the max basal and the min bolus as the min basal.
 */
class InsightPluginLimitationsTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var insightDbHelper: InsightDbHelper
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var insightDatabase: InsightDatabase
    @Mock lateinit var bolusProgressData: BolusProgressData
    @Mock lateinit var blePreCheck: BlePreCheck
    @Mock lateinit var connectionService: InsightConnectionService

    private lateinit var sut: InsightPlugin

    /** The limits the pump reports: four different numbers, so a mix-up shows. */
    private val limits: Map<Class<out ParameterBlock>, ParameterBlock> = mapOf(
        MaxBolusAmountBlock::class.java to MaxBolusAmountBlock().also { it.amountLimitation = 25.0 },
        FactoryMinBolusAmountBlock::class.java to FactoryMinBolusAmountBlock().also { it.amountLimitation = 0.05 },
        MaxBasalAmountBlock::class.java to MaxBasalAmountBlock().also { it.amountLimitation = 2.5 },
        // Not 0.02, which the description already has from the pump type: a read that does not happen must show.
        FactoryMinBasalAmountBlock::class.java to FactoryMinBasalAmountBlock().also { it.amountLimitation = 0.03 }
    )

    @BeforeEach
    fun setUp() {
        sut = InsightPlugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context, dateUtil,
            insightDbHelper, pumpSync, insightDatabase, pumpEnactResultProvider,
            notificationManager, ch, bolusProgressData,
            CoroutineScope(Dispatchers.Unconfined), aapsSchedulers, blePreCheck
        )
        sut.connectionService = connectionService
        // Each block read is answered at once with the block from [limits].
        whenever(connectionService.requestMessage(any<AppLayerMessage>())).thenAnswer { invocation ->
            val message = invocation.getArgument<AppLayerMessage>(0)
            val response = if (message is ReadParameterBlockMessage)
                mock<ReadParameterBlockMessage>().also { whenever(it.parameterBlock).thenReturn(limits[message.parameterBlockId]) }
            else message
            MessageRequest(message).also { it.response = response }
        }
    }

    @Test
    fun theBasalLimitsComeFromTheBasalBlocks() {
        sut.fetchLimitations()

        assertThat(sut.pumpDescription.basalMaximumRate).isEqualTo(2.5)
        assertThat(sut.pumpDescription.basalMinimumRate).isEqualTo(0.03)
    }

    @Test
    fun theBolusLimitsComeFromTheBolusBlocks() {
        sut.fetchLimitations()

        assertThat(sut.applyBolusConstraints(PumpInsulin(30.0)).cU).isEqualTo(25.0)
        // Below the min bolus nothing can be given.
        assertThat(sut.applyBolusConstraints(PumpInsulin(0.04)).cU).isEqualTo(0.0)
    }
}
