package app.aaps.plugins.main.extensions

import app.aaps.core.data.model.BS
import app.aaps.core.data.time.T
import app.aaps.core.objects.extensions.iobCalc
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BolusExtensionKtTest : TestBaseWithProfile() {

    @Test fun iobCalc() {
        val dia = someICfg.dia

        val bolus = BS(timestamp = now - 1, amount = 1.0, type = BS.Type.NORMAL, iCfg = someICfg)
        // there should be almost full IOB after now
        assertThat(bolus.iobCalc(now).iobContrib).isWithin(0.01).of(1.0)
        // there should be less than 5% after DIA -1
        assertThat(bolus.iobCalc(now + T.hours(dia.toLong() - 1).msecs()).iobContrib).isLessThan(0.05)
        // there should be zero after DIA
        assertThat(bolus.iobCalc(now + T.hours(dia.toLong() + 1).msecs()).iobContrib).isEqualTo(0.0)
        // no IOB for invalid record
        bolus.isValid = false
        assertThat(bolus.iobCalc(now + T.hours(1).msecs()).iobContrib).isEqualTo(0.0)
        bolus.isValid = true
        bolus.type = BS.Type.PRIMING
        assertThat(bolus.iobCalc(now + T.hours(1).msecs()).iobContrib).isEqualTo(0.0)
    }
}
