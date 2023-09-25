package info.nightscout.core.extensions

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.main.extensions.highValueToUnitsToString
import app.aaps.core.main.extensions.lowValueToUnitsToString
import app.aaps.core.main.extensions.target
import app.aaps.database.entities.TemporaryTarget
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TemporaryTargetExtensionKtTest : TestBaseWithProfile() {

    private val temporaryTarget = TemporaryTarget(
        id = 0,
        version = 0,
        dateCreated = -1,
        isValid = true,
        referenceId = null,
        interfaceIDs_backing = null,
        timestamp = 0,
        utcOffset = 0,
        reason = TemporaryTarget.Reason.AUTOMATION,
        highTarget = 120.0,
        lowTarget = 110.0,
        duration = 1800000
    )

    @Test
    fun lowValueToUnitsToString() {
        assertThat(temporaryTarget.lowValueToUnitsToString(GlucoseUnit.MGDL, decimalFormatter)).isEqualTo("110")
        assertThat(temporaryTarget.lowValueToUnitsToString(GlucoseUnit.MMOL, decimalFormatter)).isEqualTo("6.1")
    }

    @Test
    fun highValueToUnitsToString() {
        assertThat(temporaryTarget.highValueToUnitsToString(GlucoseUnit.MGDL, decimalFormatter)).isEqualTo("120")
        assertThat(temporaryTarget.highValueToUnitsToString(GlucoseUnit.MMOL, decimalFormatter)).isEqualTo("6.7")
    }

    @Test
    fun target() {
        assertThat(temporaryTarget.target()).isEqualTo(115.0)
    }
}
