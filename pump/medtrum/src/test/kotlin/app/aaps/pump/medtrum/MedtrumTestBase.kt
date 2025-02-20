package app.aaps.pump.medtrum

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.pump.medtrum.keys.MedtrumStringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock
import org.mockito.Mockito

open class MedtrumTestBase : TestBaseWithProfile() {

    @Mock lateinit var tddCalculator: TddCalculator
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage

    lateinit var medtrumPump: MedtrumPump

    @BeforeEach
    fun setup() {
        Mockito.`when`(preferences.get(MedtrumStringNonKey.ActiveAlarms)).thenReturn("")
        medtrumPump = MedtrumPump(aapsLogger, rh, preferences, dateUtil, pumpSync, temporaryBasalStorage)
    }
}
