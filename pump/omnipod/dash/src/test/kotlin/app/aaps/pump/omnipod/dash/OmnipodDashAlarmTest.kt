package app.aaps.pump.omnipod.dash

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionKeys
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManagerImpl
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.shared.tests.AAPSLoggerTest
import app.aaps.shared.tests.rx.TestAapsSchedulers
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import app.aaps.pump.omnipod.common.R as CommonR

class OmnipodDashAlarmTest {

    @Test
    fun `a received Dash fault updates pod state and posts an audible notification`() = runTest {
        val logger = AAPSLoggerTest()
        val preferences: Preferences = mock()
        val podState = OmnipodDashPodStateManagerImpl(logger, mock(), preferences).apply {
            activationProgress = ActivationProgress.COMPLETED
            uniqueId = 2L
            bluetoothAddress = "00:11:22:33:44:55"
            ltk = ByteArray(16)
        }
        val ids = Ids.forController(Id.fromInt(1), Id.fromInt(2))
        val msgIO: MessageIO = mock()
        val enDecrypt: EnDecrypt = mock()
        // Decrypted Dash reply from the report, with a test pod ID in the envelope.
        val replyHex = "302e303d00200000000214180216020d0000000e003e10000403ff0004000008000000000000"
        val reply = MessagePacket(
            type = MessageType.ENCRYPTED,
            source = ids.podId,
            destination = ids.myId,
            payload = ByteArray(replyHex.length / 2) { replyHex.substring(it * 2, it * 2 + 2).toInt(16).toByte() },
            sequenceNumber = 2
        )
        whenever(msgIO.receiveMessage()).thenReturn(reply)
        whenever(msgIO.sendMessage(any())).thenReturn(MessageSendSuccess)
        whenever(enDecrypt.decrypt(reply)).thenReturn(reply)
        whenever(enDecrypt.encrypt(any())).thenAnswer { it.getArgument<MessagePacket>(0) }
        val session = Session(logger, msgIO, ids, SessionKeys(ByteArray(16), Nonce(ByteArray(8), 0), 1), enDecrypt)
        val connection: BleConnection = mock()
        whenever(connection.connectionState()).thenReturn(Connected)
        whenever(connection.session).thenReturn(session)
        val connectionFactory: BleConnectionFactory = mock()
        whenever(connectionFactory.createConnection(any())).thenReturn(connection)
        val deviceManager: BleDeviceManager = mock()
        whenever(deviceManager.isBluetoothAvailable()).thenReturn(true)
        whenever(deviceManager.ensureBondedIfRequired(any())).thenReturn(true)
        val bleManager = OmnipodDashBleManagerImpl(logger, podState, mock(), connectionFactory, deviceManager)
        bleManager.connect().test().assertComplete().assertNoErrors()
        val schedulers = TestAapsSchedulers()
        val manager = OmnipodDashManagerImpl(logger, podState, bleManager, schedulers)
        val history: DashHistory = mock()
        whenever(history.updateFromState(podState)).thenReturn(Completable.complete())
        val pumpSync: PumpSync = mock()
        whenever(pumpSync.expectedPumpState()).thenReturn(PumpSync.PumpState(null, null, null, null, "2"))
        val rh: ResourceHelper = mock()
        whenever(
            rh.gs(
                eq(CommonR.string.omnipod_common_pod_status_pod_fault_description),
                eq(AlarmType.ALARM_SAWCOP_RESET.code),
                eq(AlarmType.ALARM_SAWCOP_RESET.toString())
            )
        ).thenReturn("Pod fault")
        whenever(rh.gs(eq(CommonR.string.omnipod_common_pdm_ref), any<String>())).thenReturn("Ref: test")
        val notifications: NotificationManager = mock()
        val plugin = OmnipodDashPumpPlugin(
            aapsLogger = logger,
            rh = rh,
            preferences = preferences,
            commandQueue = mock(),
            omnipodManager = manager,
            podStateManager = podState,
            history = history,
            pumpSync = pumpSync,
            rxBus = mock(),
            aapsSchedulers = schedulers,
            uiInteraction = mock(),
            notificationManager = notifications,
            pumpEnactResultProvider = { mock() },
            bolusProgressData = mock(),
            dashHistoryDatabase = mock(),
            protectionCheck = mock(),
            blePreCheck = mock()
        )

        plugin.getPumpStatus("Refresh")

        assertThat(podState.podStatus).isEqualTo(PodStatus.ALARM)
        assertThat(podState.deliveryStatus).isEqualTo(DeliveryStatus.SUSPENDED)
        assertThat(podState.alarmType).isEqualTo(AlarmType.ALARM_SAWCOP_RESET)
        assertThat(podState.alarmSynced).isTrue()
        verify(notifications).post(
            id = eq(NotificationId.OMNIPOD_POD_FAULT),
            text = eq("Pod fault\nRef: test"),
            level = eq(NotificationId.OMNIPOD_POD_FAULT.defaultLevel),
            validMinutes = eq(0),
            sound = eq(AlarmSound.BOLUS_ERROR),
            actions = eq(emptyList()),
            validityCheck = anyOrNull()
        )
        verify(pumpSync).insertAnnouncement(
            error = eq("Pod fault\nRef: test"),
            pumpId = any(),
            pumpType = eq(PumpType.OMNIPOD_DASH),
            pumpSerial = eq("2")
        )
    }
}
