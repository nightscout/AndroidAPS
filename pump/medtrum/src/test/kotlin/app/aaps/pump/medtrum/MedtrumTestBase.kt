package app.aaps.pump.medtrum

import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.pump.medtrum.keys.MedtrumStringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

open class MedtrumTestBase : TestBaseWithProfile() {

    @Mock lateinit var tddCalculator: TddCalculator
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage

    val bolusProgressData = BolusProgressData()
    lateinit var medtrumPump: MedtrumPump

    @BeforeEach
    fun setup() {
        whenever(preferences.get(MedtrumStringNonKey.ActiveAlarms)).thenReturn("")
        // Default mock returns for suspend PumpSync methods
        runBlocking {
            whenever(pumpSync.syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())).thenReturn(true)
            whenever(pumpSync.syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())).thenReturn(true)
            whenever(pumpSync.invalidateTemporaryBasalWithPumpId(any(), any(), any())).thenReturn(true)
            whenever(pumpSync.insertTherapyEventIfNewWithTimestamp(any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(true)
            whenever(pumpSync.syncBolusWithTempId(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any())).thenReturn(true)
            whenever(pumpSync.syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any())).thenReturn(true)
            whenever(pumpSync.syncExtendedBolusWithPumpId(any(), any(), any(), any(), any(), any(), any())).thenReturn(true)
            whenever(pumpSync.createOrUpdateTotalDailyDose(any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(true)
        }
        medtrumPump = MedtrumPump(aapsLogger, rh, preferences, dateUtil, pumpSync, temporaryBasalStorage, bolusProgressData)
    }
}
