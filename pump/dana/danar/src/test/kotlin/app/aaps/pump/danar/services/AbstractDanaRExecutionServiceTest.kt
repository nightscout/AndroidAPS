package app.aaps.pump.danar.services

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.pump.rfcomm.RfcommSocket
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danar.SerialIOThread
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MessageHashTableBase
import app.aaps.pump.danar.comm.MsgBolusStop
import app.aaps.pump.danar.comm.MsgSetBasalProfile
import app.aaps.pump.danar.comm.MsgSettingBasal
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import kotlin.math.roundToInt

class AbstractDanaRExecutionServiceTest : TestBaseWithProfile() {

    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var messageHashTable: MessageHashTableBase
    @Mock lateinit var pumpEnactResult: PumpEnactResult
    @Mock lateinit var danaRPlugin: DanaRPlugin

    lateinit var danaPump: DanaPump

    init {
        addInjector { injector ->
            if (injector is MessageBase) {
                injector.aapsLogger = aapsLogger
                injector.danaPump = danaPump
                injector.danaRPlugin = danaRPlugin
            }
            if (injector is MsgBolusStop) {
                injector.aapsLogger = aapsLogger
                injector.rh = rh
                injector.danaPump = danaPump
                injector.bolusProgressData = BolusProgressData(ch, CoroutineScope(Dispatchers.Unconfined))
            }
        }
    }

    private lateinit var testService: TestDanaRExecutionService

    inner class TestDanaRExecutionService : AbstractDanaRExecutionService() {

        override fun messageHashTable(): MessageHashTableBase = messageHashTable
        override suspend fun updateBasalsInPump(profile: Profile): Boolean = true
        override suspend fun getPumpStatus() {}
        override fun loadEvents(): PumpEnactResult = pumpEnactResult
        override fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean = true
        override fun highTempBasal(percent: Int, durationInMinutes: Int): Boolean = false
        override fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean = false
        override fun tempBasal(percent: Int, durationInHours: Int): Boolean = true
        override fun tempBasalStop(): Boolean = true
        override fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean = true
        override fun extendedBolusStop(): Boolean = true
        override fun setUserOptions(): PumpEnactResult = pumpEnactResult

        fun useLink(serialIOThread: SerialIOThread?) {
            mSerialIOThread = serialIOThread
        }

        fun send(message: MessageBase): Boolean = sendToPump(message)
        fun basalGiven(written: Boolean, read: MessageBase, basal: Array<Double>): Boolean = pumpGivesBasal(written, read, basal)
    }

    /** A link to a pump that answers each message with the bytes [answer] gives, or not at all for null. */
    private fun pumpAnswers(answer: (MessageBase) -> ByteArray?) {
        val serialIOThread = mock(SerialIOThread::class.java)
        doAnswer { invocation ->
            val message = invocation.getArgument<MessageBase>(0)
            answer(message)?.let {
                message.handleMessage(it)
                message.isReceived = true
            }
            null
        }.whenever(serialIOThread).sendMessage(any())
        testService.useLink(serialIOThread)
    }

    /** The answer to a write: 1 when the pump took it. */
    private fun result(value: Int): ByteArray = ByteArray(ANSWER_SIZE).also { it[6] = value.toByte() }

    /** The answer to a basal read: 24 rates in hundredths of a unit, two bytes each. */
    private fun basalAnswer(rate: Double): ByteArray = ByteArray(ANSWER_SIZE).also {
        val hundredths = (rate * 100).roundToInt()
        for (hour in 0 until HOURS) {
            it[6 + 2 * hour] = (hundredths shr 8).toByte()
            it[6 + 2 * hour + 1] = (hundredths and 0xFF).toByte()
        }
    }

    /** As `DanaPump.buildDanaRProfileRecord` makes it: the rate and a little more against a cut. */
    private fun profileRecord(rate: Double): Array<Double> = Array(HOURS) { rate + 0.00001 }

    @BeforeEach
    fun setup() {
        `when`(rh.gs(anyInt())).thenReturn("test string")
        `when`(rh.gs(anyInt(), any())).thenReturn("test string")
        `when`(danaRPlugin.pumpDescription).thenReturn(PumpDescription())

        danaPump = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)
        testService = TestDanaRExecutionService()
        testService.aapsLogger = aapsLogger
        testService.rxBus = rxBus
        testService.preferences = preferences
        testService.context = context
        testService.rh = rh
        testService.danaPump = danaPump
        testService.dateUtil = dateUtil
        testService.pumpSync = pumpSync
        testService.activePlugin = activePlugin
        testService.notificationManager = notificationManager
        testService.bolusProgressData = BolusProgressData(ch, CoroutineScope(Dispatchers.Unconfined))
        testService.pumpEnactResultProvider = pumpEnactResultProvider
        testService.rfcommTransport = mock()
        testService.injector = injector
        testService.appScope = CoroutineScope(Dispatchers.Unconfined)
    }

    @Test
    fun testIsConnected() {
        assertThat(testService.isConnected).isFalse()
    }

    @Test
    fun testIsHandshakeInProgress() {
        assertThat(testService.isHandshakeInProgress).isFalse()
    }

    @Test
    fun testDisconnect() {
        testService.disconnect("test")
        // Verify no crash when mSerialIOThread is null
    }

    @Test
    fun testStopConnecting() {
        testService.stopConnecting()
        // Verify no crash when mSerialIOThread is null
    }

    @Test
    fun testBolusStop_notConnected() {
        testService.bolusStop()

        assertThat(danaPump.bolusStopForced).isTrue()
        assertThat(danaPump.bolusStopped).isTrue()
    }

    @Test
    fun testLoadHistory_notConnected() {
        val result = testService.loadHistory(RecordTypes.RECORD_TYPE_BOLUS)

        assertThat(result).isNotNull()
    }

    @Test
    fun testLoadHistory_withDifferentTypes() {
        // Test all record types
        val types = arrayOf(
            RecordTypes.RECORD_TYPE_ALARM,
            RecordTypes.RECORD_TYPE_BASALHOUR,
            RecordTypes.RECORD_TYPE_BOLUS,
            RecordTypes.RECORD_TYPE_CARBO,
            RecordTypes.RECORD_TYPE_DAILY,
            RecordTypes.RECORD_TYPE_ERROR,
            RecordTypes.RECORD_TYPE_GLUCOSE,
            RecordTypes.RECORD_TYPE_REFILL,
            RecordTypes.RECORD_TYPE_SUSPEND
        )

        types.forEach { type ->
            val result = testService.loadHistory(type)
            assertThat(result).isNotNull()
        }
    }

    @Test
    fun testDoSanityCheck_temporaryBasalMismatch() = runTest {
        val temporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        val activePump = mock(app.aaps.core.interfaces.pump.PumpWithConcentration::class.java)

        `when`(temporaryBasal.rate).thenReturn(150.0)
        `when`(temporaryBasal.timestamp).thenReturn(1000000L)

        // Create real PumpState for proper destructuring
        val pumpState = PumpSync.PumpState(temporaryBasal, null, null, null, "TEST123")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)

        // Set underlying properties to make isTempBasalInProgress true
        danaPump.tempBasalPercent = 100
        danaPump.tempBasalStart = 1000000L
        danaPump.tempBasalDuration = 1800000L // 30 minutes in milliseconds
        `when`(dateUtil.now()).thenReturn(1500000L) // Within the temp basal range
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")

        testService.doSanityCheck()

        // Verify that synchronization was attempted
        assertThat(mockingDetails(notificationManager).invocations.any { it.method.name == "post" }).isTrue()
    }

    @Test
    fun testDoSanityCheck_noTemporaryBasalInAAPSButInPump() = runTest {
        val activePump = mock(app.aaps.core.interfaces.pump.PumpWithConcentration::class.java)

        // Create real PumpState for proper destructuring
        val pumpState = PumpSync.PumpState(null, null, null, null, "TEST123")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)

        // Set underlying properties to make isTempBasalInProgress true
        danaPump.tempBasalPercent = 120
        danaPump.tempBasalStart = 1000000L
        danaPump.tempBasalDuration = 1800000L // 30 minutes in milliseconds
        `when`(dateUtil.now()).thenReturn(1500000L) // Within the temp basal range
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")

        testService.doSanityCheck()

        // Verify notification was added
        assertThat(mockingDetails(notificationManager).invocations.any { it.method.name == "post" }).isTrue()
    }

    @Test
    fun testDoSanityCheck_temporaryBasalInAAPSButNotInPump() = runTest {
        val temporaryBasal = mock(PumpSync.PumpState.TemporaryBasal::class.java)
        val activePump = mock(app.aaps.core.interfaces.pump.PumpWithConcentration::class.java)

        // Set dateUtil.now() first, before checking isTempBasalInProgress
        `when`(dateUtil.now()).thenReturn(2000000L)

        `when`(temporaryBasal.rate).thenReturn(150.0)
        `when`(temporaryBasal.timestamp).thenReturn(1000000L)

        // Create real PumpState for proper destructuring
        val pumpState = PumpSync.PumpState(temporaryBasal, null, null, null, "TEST123")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)

        // Ensure pump shows no temp basal (tempBasalStart = 0 by default)
        danaPump.isTempBasalInProgress = false
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")

        testService.doSanityCheck()

        // Verify synchronization
        assertThat(mockingDetails(notificationManager).invocations.any { it.method.name == "post" }).isTrue()
    }

    @Test
    fun testDoSanityCheck_extendedBolusMismatch() = runTest {
        val extendedBolus = mock(PumpSync.PumpState.ExtendedBolus::class.java)
        val activePump = mock(app.aaps.core.interfaces.pump.PumpWithConcentration::class.java)

        // Set dateUtil.now() FIRST to ensure it's available for all property getters
        `when`(dateUtil.now()).thenReturn(1500000L) // Within the extended bolus range

        `when`(extendedBolus.rate).thenReturn(1.5)
        `when`(extendedBolus.timestamp).thenReturn(500000L) // Make timestamp different enough

        // Create real PumpState for proper destructuring
        val pumpState = PumpSync.PumpState(null, extendedBolus, null, null, "TEST123")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)

        // Set underlying properties to make isExtendedInProgress true
        danaPump.extendedBolusStart = 1000000L
        danaPump.extendedBolusAmount = 3.0
        danaPump.extendedBolusDuration = 7200000L // 120 minutes in milliseconds
        danaPump.extendedBolusAbsoluteRate = 2.0 // Different rate to trigger mismatch

        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")
        `when`(activePump.isFakingTempsByExtendedBoluses).thenReturn(false)

        testService.doSanityCheck()

        // Verify notification
        assertThat(mockingDetails(notificationManager).invocations.any { it.method.name == "post" }).isTrue()
    }

    /**
     * An extended bolus the user set on the pump is stored with its own start as its pump id. With the start of the temp
     * basal, 0 when none runs, a second one only updated the ended record of the first, and AAPS never counted it.
     */
    @Test
    fun testDoSanityCheck_extendedBolusOnThePumpHasItsOwnPumpId() = runTest {
        val activePump = mock(PumpWithConcentration::class.java)
        `when`(dateUtil.now()).thenReturn(1500000L)
        `when`(pumpSync.expectedPumpState()).thenReturn(PumpSync.PumpState(null, null, null, null, "TEST123"))
        danaPump.isTempBasalInProgress = false
        danaPump.extendedBolusStart = 1000000L
        danaPump.extendedBolusAmount = 1.0
        danaPump.extendedBolusDuration = 1800000L
        `when`(activePlugin.activePump).thenReturn(activePump)
        `when`(activePump.model()).thenReturn(PumpType.DANA_R)
        `when`(activePump.serialNumber()).thenReturn("TEST123")

        testService.doSanityCheck()

        val sync = mockingDetails(pumpSync).invocations.single { it.method.name.startsWith("syncExtendedBolusWithPumpId") }
        // timestamp, amount, duration, emulating a temp basal, pump id, ...
        assertThat(sync.arguments[4]).isEqualTo(1000000L)
    }

    @Test
    fun testDoSanityCheck_allMatch() = runTest {
        // Create real PumpState for proper destructuring
        val pumpState = PumpSync.PumpState(null, null, null, null, "TEST123")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)

        danaPump.isTempBasalInProgress = false
        danaPump.isExtendedInProgress = false
        `when`(dateUtil.now()).thenReturn(2000000L)

        testService.doSanityCheck()

        // Verify no notifications when everything matches
        assertThat(mockingDetails(notificationManager).invocations.none { it.method.name == "post" }).isTrue()
    }

    @Test
    fun testGetSocketForSelectedPump_deviceNotFound() {
        `when`(preferences.get(DanaStringNonKey.RName)).thenReturn("TestPump")
        `when`(testService.rfcommTransport.getSocketForDevice("TestPump")).thenReturn(null)

        testService.getSocketForSelectedPump()

        // Should handle null socket gracefully
    }

    // The queue tries to connect about every second. A message for every try piled up on the screen.
    @Test
    fun deviceNotFound_isShownOnceUntilTheDeviceIsFoundAgain() {
        val bus = mock(RxBus::class.java)
        testService.rxBus = bus
        `when`(preferences.get(DanaStringNonKey.RName)).thenReturn("TestPump")
        `when`(testService.rfcommTransport.getSocketForDevice("TestPump")).thenReturn(null)

        repeat(3) { testService.getSocketForSelectedPump() }
        verify(bus, times(1)).send(any<EventShowSnackbar>())

        `when`(testService.rfcommTransport.getSocketForDevice("TestPump")).thenReturn(mock(RfcommSocket::class.java))
        testService.getSocketForSelectedPump()
        `when`(testService.rfcommTransport.getSocketForDevice("TestPump")).thenReturn(null)
        testService.getSocketForSelectedPump()

        verify(bus, times(2)).send(any<EventShowSnackbar>())
    }

    @Test
    fun aMessageWithNoLinkIsNotDone() {
        testService.useLink(null)

        assertThat(testService.send(MsgSetBasalProfile(injector, 0, profileRecord(RATE)))).isFalse()
    }

    // `failed` is set only from an answer, so it stays false here. A profile write with no answer was taken as done.
    @Test
    fun aMessageWithNoAnswerIsNotDone() {
        pumpAnswers { null }
        val message = MsgSetBasalProfile(injector, 0, profileRecord(RATE))

        assertThat(testService.send(message)).isFalse()
        assertThat(message.failed).isFalse()
    }

    @Test
    fun aMessageThePumpRefusesIsNotDone() {
        pumpAnswers { result(2) }

        assertThat(testService.send(MsgSetBasalProfile(injector, 0, profileRecord(RATE)))).isFalse()
    }

    @Test
    fun aMessageThePumpTakesIsDone() {
        pumpAnswers { result(1) }

        assertThat(testService.send(MsgSetBasalProfile(injector, 0, profileRecord(RATE)))).isTrue()
    }

    @Test
    fun aProfileWriteThePumpAnsweredIsDoneWithNoRead() {
        pumpAnswers { null }

        assertThat(testService.basalGiven(true, MsgSettingBasal(injector), profileRecord(RATE))).isTrue()
    }

    // The pump took the write and its answer was lost: what it gives tells. Taken as failed, this rang a false alarm.
    @Test
    fun aProfileWriteWithNoAnswerIsDoneWhenThePumpGivesItsBasal() {
        pumpAnswers { basalAnswer(RATE) }

        assertThat(testService.basalGiven(false, MsgSettingBasal(injector), profileRecord(RATE))).isTrue()
    }

    @Test
    fun aProfileWriteWithNoAnswerIsNotDoneWhenThePumpGivesAnotherBasal() {
        pumpAnswers { basalAnswer(OTHER_RATE) }

        assertThat(testService.basalGiven(false, MsgSettingBasal(injector), profileRecord(RATE))).isFalse()
    }

    // A basal read before the write is no proof: the read after it must be answered.
    @Test
    fun aProfileWriteIsNotDoneWhenTheReadBackIsNotAnsweredEither() {
        pumpAnswers { null }
        danaPump.pumpProfiles = Array(4) { Array(48) { RATE } }

        assertThat(testService.basalGiven(false, MsgSettingBasal(injector), profileRecord(RATE))).isFalse()
    }

    private companion object {

        const val ANSWER_SIZE = 60
        const val HOURS = 24
        const val RATE = 0.8
        const val OTHER_RATE = 0.5
    }
}
