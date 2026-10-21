package app.aaps.pump.omnipod.common.bledriver.pod.state

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.pair.PairResult
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlarmType
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

/**
 * Tests for [OmnipodDashPodStateManagerImpl.updateFromPairing].
 *
 * A pod that was not cleanly discarded (app restart, pod physically removed without using
 * Discard Pod, ...) can leave a frozen fault snapshot (alarmType/pdmRef/alarmSynced) in
 * [OmnipodDashPodStateManagerImpl.podState]. Pairing a new pod must clear that snapshot, or the
 * new pod's overview would keep showing the old pod's Ref code, and a genuine new fault would
 * never be announced (alarmSynced gates that announcement, see [OmnipodDashPodStateManagerImpl.alarmSynced]).
 */
class OmnipodDashPodStateManagerImplUpdateFromPairingTest : TestBase() {

    @Mock lateinit var preferences: Preferences

    private lateinit var sut: OmnipodDashPodStateManagerImpl

    @BeforeEach fun setUp() {
        sut = OmnipodDashPodStateManagerImpl(aapsLogger, rxBus, preferences)
    }

    @Test fun `pairing a new pod clears a fault snapshot left over from a previous pod`() {
        sut.podState.alarmType = AlarmType.ALARM_EMPTY_RESERVOIR
        sut.podState.pdmRef = "14-00072-20000-024"
        sut.podState.alarmSynced = true

        sut.updateFromPairing(Id(byteArrayOf(1, 2, 3, 4)), PairResult(ltk = ByteArray(16) { (it + 1).toByte() }, msgSeq = 0))

        assertThat(sut.alarmType).isNull()
        assertThat(sut.pdmRef).isNull()
        assertThat(sut.alarmSynced).isFalse()
    }

    @Test fun `pairing still sets the pairing result fields`() {
        val uniqueId = Id(byteArrayOf(1, 2, 3, 4))
        val pairResult = PairResult(ltk = ByteArray(16) { (it + 1).toByte() }, msgSeq = 3)

        sut.updateFromPairing(uniqueId, pairResult)

        assertThat(sut.podState.uniqueId).isEqualTo(uniqueId.toLong())
        assertThat(sut.podState.ltk).isEqualTo(pairResult.ltk)
        assertThat(sut.podState.eapAkaSequenceNumber).isEqualTo(1)
    }
}
