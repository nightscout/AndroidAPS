package info.nightscout.ui.extensions

import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class InterfaceIDsExtensionKtTest : TestBase() {

    @Test
    fun isPumpHistory() {
        val sut = InterfaceIDs()
        assertThat(sut.isPumpHistory()).isFalse()
        sut.pumpId = 123
        assertThat(sut.isPumpHistory()).isFalse()
        sut.pumpId = null
        sut.pumpSerial = "123"
        assertThat(sut.isPumpHistory()).isFalse()
        sut.pumpId = 123
        sut.pumpSerial = "123"
        assertThat(sut.isPumpHistory()).isTrue()
    }
}