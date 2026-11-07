package app.aaps.pump.eopatch.core.ble

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PatchFuncTest {

    @Test
    fun `should have correct noCrypt flags`() {
        assertThat(PatchFunc.REQUEST_BONDING.noCrypt).isFalse()
        assertThat(PatchFunc.UPDATE_CONNECTION.noCrypt).isTrue()
        assertThat(PatchFunc.GET_AE_CODES.noCrypt).isTrue()
        assertThat(PatchFunc.SET_PUBLIC_KEY.noCrypt).isTrue()
        assertThat(PatchFunc.GET_PUBLIC_KEY.noCrypt).isTrue()
        assertThat(PatchFunc.GET_SEQ_NUM.noCrypt).isTrue()
    }

    @Test
    fun `most functions should have noCrypt false`() {
        val noCryptTrue = PatchFunc.entries.filter { it.noCrypt }
        assertThat(noCryptTrue).containsExactly(
            PatchFunc.UPDATE_CONNECTION,
            PatchFunc.GET_AE_CODES,
            PatchFunc.SET_PUBLIC_KEY,
            PatchFunc.GET_PUBLIC_KEY,
            PatchFunc.GET_SEQ_NUM
        )
    }

    @Test
    fun `valueOf should resolve known functions`() {
        assertThat(PatchFunc.valueOf("START_NOW_BOLUS")).isEqualTo(PatchFunc.START_NOW_BOLUS)
        assertThat(PatchFunc.valueOf("DEACTIVATE")).isEqualTo(PatchFunc.DEACTIVATE)
    }
}
