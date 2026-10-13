package app.aaps.pump.eopatch

import app.aaps.pump.eopatch.alarm.IAlarmManager
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.shared.tests.TestBaseWithProfile
import org.mockito.Mock

open class EopatchTestBase : TestBaseWithProfile() {

    @Mock lateinit var patchManager: IPatchManager
    @Mock lateinit var patchManagerExecutor: PatchManagerExecutor
    @Mock lateinit var alarmManager: IAlarmManager
    @Mock lateinit var eopatchPreferenceManager: PreferenceManager

    lateinit var patchConfig: PatchConfig
    lateinit var normalBasalManager: NormalBasalManager

    fun prepareMocks() {
        patchConfig = PatchConfig()
        normalBasalManager = NormalBasalManager()
    }
}
