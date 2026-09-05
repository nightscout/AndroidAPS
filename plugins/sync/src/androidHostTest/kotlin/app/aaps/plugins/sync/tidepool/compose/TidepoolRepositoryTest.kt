package app.aaps.plugins.sync.tidepool.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class TidepoolRepositoryTest {

    @Mock lateinit var aapsLogger: AAPSLogger

    private lateinit var sut: TidepoolRepository

    @BeforeEach
    fun setup() {
        sut = TidepoolRepository(aapsLogger)
    }

    @Test
    fun `initial state is NONE with an empty log`() {
        assertThat(sut.connectionStatus.value).isEqualTo(AuthFlowOut.ConnectionStatus.NONE)
        assertThat(sut.logList.value).isEmpty()
    }

    @Test
    fun `updateConnectionStatus publishes the new status`() {
        sut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED)
        assertThat(sut.connectionStatus.value).isEqualTo(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED)
    }

    @Test
    fun `addLog prepends entries so the newest is first`() {
        sut.addLog("first")
        sut.addLog("second")
        val logs = sut.logList.value
        assertThat(logs).hasSize(2)
        assertThat(logs[0].status).isEqualTo("second")
        assertThat(logs[1].status).isEqualTo("first")
    }

    @Test
    fun `addLog caps the list at 100 entries keeping the newest`() {
        for (i in 1..150) sut.addLog("log $i")
        val logs = sut.logList.value
        assertThat(logs).hasSize(100)
        assertThat(logs.first().status).isEqualTo("log 150")
        assertThat(logs.last().status).isEqualTo("log 51")
    }

    @Test
    fun `clearLog empties the list`() {
        sut.addLog("something")
        sut.clearLog()
        assertThat(sut.logList.value).isEmpty()
    }
}
