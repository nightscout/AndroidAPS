package app.aaps.pump.insight

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.ReadParameterBlockMessage
import app.aaps.pump.insight.app_layer.configuration.CloseConfigurationWriteSessionMessage
import app.aaps.pump.insight.app_layer.configuration.OpenConfigurationWriteSessionMessage
import app.aaps.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage
import app.aaps.pump.insight.app_layer.parameter_blocks.ActiveBRProfileBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile1Block
import app.aaps.pump.insight.connection_service.ConfigurationWriteSessionRequest
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.insight.connection_service.MessageRequest
import app.aaps.pump.insight.database.InsightDatabase
import app.aaps.pump.insight.database.InsightDbHelper
import app.aaps.pump.insight.descriptors.BasalProfile
import app.aaps.pump.insight.descriptors.BasalProfileBlock
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
 * Two things are pinned here, and both are about what a lost connection leaves behind.
 *
 * The two blocks must go into ONE write session: the pump only applies a session when it is
 * closed, so grouping them means a link lost part way through changes nothing at all. Written as
 * two sessions, the pump committed the switch to `PROFILE_1` on its own, and a failure after that
 * left it running `PROFILE_1` while `PROFILE_1` still held the old rates - a wrong basal rate with
 * nothing to warn the user.
 *
 * Inside that session the rates are written before the activation, so that even if the pump
 * rejected the second write while the link was up, the block already in is the harmless one.
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

    /** One entry per write session the plugin opened, holding the blocks written inside it. */
    private val sessions = mutableListOf<List<WriteConfigurationBlockMessage>>()

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

    /** A request the caller can `await()` without blocking, answered with the message itself. */
    private fun <T : AppLayerMessage> answered(message: T) =
        MessageRequest(message).also { it.response = message }

    @BeforeEach
    fun setUp() {
        sut = InsightPlugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context, dateUtil,
            insightDbHelper, pumpSync, insightDatabase, pumpEnactResultProvider,
            notificationManager, ch, bolusProgressData,
            CoroutineScope(Dispatchers.Unconfined), aapsSchedulers, blePreCheck
        )
        sut.connectionService = connectionService
        whenever(connectionService.requestConfigurationWrites(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val messages = invocation.getArgument<List<WriteConfigurationBlockMessage>>(0)
            sessions += messages
            ConfigurationWriteSessionRequest(
                answered(OpenConfigurationWriteSessionMessage()),
                messages.map { answered(it) },
                answered(CloseConfigurationWriteSessionMessage())
            )
        }
        // The status read that follows the write goes through requestMessage. Answer it so it does
        // not block; whatever it makes of the answer is caught inside setNewBasalProfile anyway.
        whenever(connectionService.requestMessage(any<AppLayerMessage>())).thenAnswer { invocation ->
            answered(invocation.getArgument<AppLayerMessage>(0))
        }
    }

    private fun writtenBlocks() = sessions.single().map { it.parameterBlock }

    @Test
    fun bothBlocksAreWrittenInASingleSession() = runBlocking {
        sut.setNewBasalProfile(profile)

        assertThat(sessions).hasSize(1)
        assertThat(sessions.single()).hasSize(2)
    }

    @Test
    fun theRatesAreWrittenBeforeTheProfileIsActivated() = runBlocking {
        sut.setNewBasalProfile(profile)

        val blocks = writtenBlocks()
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

    // --- #2454: the running basal is what the pump keeps ---

    private fun blocks(vararg blocks: Pair<Int, Double>) = blocks.map { (minutes, amount) -> BasalProfileBlock().apply { duration = minutes; basalAmount = amount } }

    /** The pump answers the read of profile 1 with [reported], or with nothing when it is null. */
    private fun pumpReports(reported: List<BasalProfileBlock>?) {
        whenever(connectionService.isPaired).thenReturn(true)
        whenever(connectionService.requestMessage(any<AppLayerMessage>())).thenAnswer { invocation ->
            val message = invocation.getArgument<AppLayerMessage>(0)
            if (message is ReadParameterBlockMessage && message.parameterBlockId == BRProfile1Block::class.java) {
                val answer = mock<ReadParameterBlockMessage>()
                whenever(answer.parameterBlock).thenReturn(reported?.let { BRProfile1Block().apply { profileBlocks = it.toMutableList() } })
                MessageRequest(message).also { it.response = answer }
            } else answered(message)
        }
    }

    @Test
    fun afterTheSetTheProfileIsReadBackAndThatIsTheDeliveredBasal() = runBlocking {
        pumpReports(blocks(360 to 0.49, 720 to 0.8, 360 to 0.6))

        sut.setNewBasalProfile(profile)

        val delivered = sut.deliveredBasal()!!
        assertThat(delivered.map { it.timeAsSeconds }).containsExactlyElementsIn((0..23).map { it * 3600 }).inOrder()
        assertThat(delivered.map { it.value }).containsExactlyElementsIn((0..23).map { if (it < 6) 0.49 else if (it < 18) 0.8 else 0.6 }).inOrder()
    }

    @Test
    fun whenTheReadFailsTheBlocksAsTheyWereSentAreTheDeliveredBasal() = runBlocking {
        pumpReports(null)

        sut.setNewBasalProfile(profile)

        assertThat(sut.deliveredBasal()!!.map { it.value }).containsExactlyElementsIn((0..23).map { if (it < 6) 0.5 else if (it < 18) 0.8 else 0.6 }).inOrder()
    }

    @Test
    fun anHourWithTwoBlocksCountsEachForItsMinutes() = runBlocking {
        pumpReports(blocks(30 to 0.5, 1410 to 1.0))

        sut.setNewBasalProfile(profile)

        val delivered = sut.deliveredBasal()!!.map { it.value }
        assertThat(delivered[0]).isEqualTo(0.75)
        assertThat(delivered.drop(1).distinct()).containsExactly(1.0)
    }

    @Test
    fun theDeliveredBasalIsUnknownBeforeAProfileWasSetOrRead() {
        whenever(connectionService.isPaired).thenReturn(true)
        assertThat(sut.deliveredBasal()).isNull()
    }

    @Test
    fun nothingIsWrittenWhenThePumpIsNotConnected() = runBlocking {
        sut.connectionService = null

        val result = sut.setNewBasalProfile(profile)

        assertThat(sessions).isEmpty()
        // Not a real failure - the profile is pushed again on reconnect, so this must not raise the
        // central "profile update failed" alarm.
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
    }
}
