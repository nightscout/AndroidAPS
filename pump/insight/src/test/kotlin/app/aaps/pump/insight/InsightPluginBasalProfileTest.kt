package app.aaps.pump.insight

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage
import app.aaps.pump.insight.app_layer.parameter_blocks.ActiveBRProfileBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile1Block
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.insight.connection_service.MessageRequest
import app.aaps.pump.insight.database.InsightDatabase
import app.aaps.pump.insight.database.InsightDbHelper
import app.aaps.pump.insight.descriptors.BasalProfile
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Covers how [InsightPlugin.setNewBasalProfile] writes a profile to the pump.
 *
 * The order of the two writes is the point. Each one is committed on its own, so writing the
 * activation first left the pump switched to `PROFILE_1` while `PROFILE_1` still held the old
 * rates whenever the second write failed - a wrong basal rate with nothing to warn the user.
 * Writing the rates first makes every failure point safe, so the order is pinned here.
 */
class InsightPluginBasalProfileTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var insightDbHelper: InsightDbHelper
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var insightDatabase: InsightDatabase
    @Mock lateinit var bolusProgressData: BolusProgressData
    @Mock lateinit var blePreCheck: BlePreCheck
    @Mock lateinit var connectionService: InsightConnectionService

    private lateinit var sut: InsightPlugin

    /** Every message the plugin handed to the connection service, in the order it did so. */
    private val sent = mutableListOf<AppLayerMessage>()

    /** Three basal blocks covering the whole day: 00:00, 06:00 and 18:00. */
    private val profile: PumpProfile = mock<PumpProfile>().also {
        whenever(it.getBasalValues()).thenReturn(
            arrayOf(
                Profile.ProfileValue(0, 0.5),
                Profile.ProfileValue(6 * 3600, 0.8),
                Profile.ProfileValue(18 * 3600, 0.6)
            )
        )
    }

    @BeforeEach
    fun setUp() {
        sut = InsightPlugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context, dateUtil,
            insightDbHelper, pumpSync, insightDatabase, pumpEnactResultProvider,
            notificationManager, ch, bolusProgressData,
            CoroutineScope(Dispatchers.Unconfined), aapsSchedulers, blePreCheck
        )
        sut.connectionService = connectionService
        // Answer every request straight away with the request itself as the response, so `await()`
        // returns instead of blocking. Good enough here: the writes ignore the response, and the
        // status read that follows is wrapped in its own catch inside setNewBasalProfile.
        whenever(connectionService.requestMessage(any<AppLayerMessage>())).thenAnswer { invocation ->
            val message = invocation.getArgument<AppLayerMessage>(0)
            sent += message
            MessageRequest(message).also { it.response = message }
        }
    }

    private fun writtenBlocks() =
        sent.filterIsInstance<WriteConfigurationBlockMessage>().map { it.parameterBlock }

    @Test
    fun theRatesAreWrittenBeforeTheProfileIsActivated() = runBlocking {
        sut.setNewBasalProfile(profile)

        val blocks = writtenBlocks()
        assertThat(blocks).hasSize(2)
        assertThat(blocks[0]).isInstanceOf(BRProfile1Block::class.java)
        assertThat(blocks[1]).isInstanceOf(ActiveBRProfileBlock::class.java)
    }

    @Test
    fun theWrittenProfileIsActivatedAsProfile1() = runBlocking {
        sut.setNewBasalProfile(profile)

        val activation = writtenBlocks().filterIsInstance<ActiveBRProfileBlock>().single()
        assertThat(activation.activeBasalProfile).isEqualTo(BasalProfile.PROFILE_1)
    }

    @Test
    fun theWrittenRatesCoverTheWholeDayInMinutes() = runBlocking {
        sut.setNewBasalProfile(profile)

        val rates = writtenBlocks().filterIsInstance<BRProfile1Block>().single()
        assertThat(rates.profileBlocks.map { it.duration }).containsExactly(360, 720, 360).inOrder()
        assertThat(rates.profileBlocks.map { it.basalAmount }).containsExactly(0.5, 0.8, 0.6).inOrder()
    }

    @Test
    fun nothingIsWrittenWhenThePumpIsNotConnected() = runBlocking {
        sut.connectionService = null

        val result = sut.setNewBasalProfile(profile)

        assertThat(sent).isEmpty()
        // Not a real failure - the profile is pushed again on reconnect, so this must not raise the
        // central "profile update failed" alarm.
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
    }
}
