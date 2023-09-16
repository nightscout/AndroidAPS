package info.nightscout.ui.extensions

import com.google.common.truth.Truth.assertThat
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sharedtests.TestBase
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