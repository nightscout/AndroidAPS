package app.aaps.ui.extensions

import app.aaps.core.data.model.IDs
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IDsExtensionKtTest : TestBase() {

    @Test
    fun isPumpHistory() {
        val sut = IDs()
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