package app.aaps.plugins.sync.smsCommunicator

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SmsActionTest {

    private var result = ""

    private class StubAction(pumpCommand: Boolean, val onRun: suspend () -> Unit) : SmsAction(pumpCommand) {
        override suspend fun run() = onRun()
    }

    @Test fun runIsInvokedAndPumpCommandFlagIsExposed() = runTest {
        val nonPumpAction: SmsAction = StubAction(pumpCommand = false) { result = "A" }
        nonPumpAction.run()
        assertThat(result).isEqualTo("A")
        assertThat(nonPumpAction.pumpCommand).isFalse()

        val pumpAction: SmsAction = StubAction(pumpCommand = true) { result = "B" }
        pumpAction.run()
        assertThat(result).isEqualTo("B")
        assertThat(pumpAction.pumpCommand).isTrue()
    }
}
