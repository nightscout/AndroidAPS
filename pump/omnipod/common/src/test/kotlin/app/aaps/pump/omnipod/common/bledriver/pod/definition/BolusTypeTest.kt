package app.aaps.pump.omnipod.common.bledriver.pod.definition

import app.aaps.core.data.model.BS
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.jupiter.api.Test

/**
 * Regression test for the AAPS 3.x pod-state migration: `OmnipodDashPodStateManager.LastBolus.bolusType`
 * used to be `BS.Type`, and Gson persists enums by name, so pod state stored by AAPS 3.x has
 * "NORMAL" where this enum only knows "DEFAULT". If this ever stops parsing, updaters from AAPS 3.x
 * with a recorded bolus lose their whole pod state (see `OmnipodDashPodStateManagerImpl.load`).
 */
class BolusTypeTest {

    private val gson = Gson()

    @Test fun `legacy AAPS 3-x NORMAL json parses as DEFAULT`() {
        val parsed = gson.fromJson("\"NORMAL\"", BolusType::class.java)

        assertThat(parsed).isEqualTo(BolusType.DEFAULT)
    }

    @Test fun `SMB json parses as SMB`() {
        val parsed = gson.fromJson("\"SMB\"", BolusType::class.java)

        assertThat(parsed).isEqualTo(BolusType.SMB)
    }

    @Test fun `DEFAULT still serializes to DEFAULT`() {
        assertThat(gson.toJson(BolusType.DEFAULT)).isEqualTo("\"DEFAULT\"")
    }

    @Test fun `BASAL_CORRECTION reports to AAPS as a plain NORMAL bolus`() {
        assertThat(BolusType.BASAL_CORRECTION.toBolusInfoBolusType()).isEqualTo(BS.Type.NORMAL)
    }

    @Test fun `every BS Type round-trips through BolusType unchanged`() {
        // Guards against future BS.Type additions (like PRIMING) silently narrowing to DEFAULT.
        for (type in BS.Type.entries) {
            assertThat(BolusType.fromBolusInfoBolusType(type).toBolusInfoBolusType()).isEqualTo(type)
        }
    }
}
