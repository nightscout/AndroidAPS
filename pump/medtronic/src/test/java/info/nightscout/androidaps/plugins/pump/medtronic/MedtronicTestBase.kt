package info.nightscout.androidaps.plugins.pump.medtronic

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.pump.common.sync.PumpSyncStorage
import info.nightscout.pump.core.utils.ByteUtil
import info.nightscout.rx.TestAapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.sharedtests.TestBase
import org.mockito.Answers
import org.mockito.Mock

open class MedtronicTestBase : TestBase() {

    var rxBus: RxBus = RxBus(TestAapsSchedulers(), aapsLogger)
    var byteUtil = ByteUtil()
    var rileyLinkUtil = RileyLinkUtil()

    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var pumpSyncStorage: PumpSyncStorage
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) lateinit var activePlugin: ActivePlugin
    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper

    lateinit var medtronicUtil: MedtronicUtil
    lateinit var decoder: MedtronicPumpHistoryDecoder

    val packetInjector = HasAndroidInjector { AndroidInjector { } }

    fun preProcessListTBR(inputList: MutableList<PumpHistoryEntry>) {

        val tbrs: MutableList<PumpHistoryEntry> = mutableListOf()

        for (pumpHistoryEntry in inputList) {
            if (pumpHistoryEntry.entryType === PumpHistoryEntryType.TempBasalRate ||
                pumpHistoryEntry.entryType === PumpHistoryEntryType.TempBasalDuration
            ) {
                tbrs.add(pumpHistoryEntry)
            }
        }

        inputList.removeAll(tbrs)

        inputList.addAll(preProcessTBRs(tbrs))

        sort(inputList)

        //return inputList

    }

    private fun preProcessTBRs(tbrsInput: MutableList<PumpHistoryEntry>): MutableList<PumpHistoryEntry> {
        val tbrs: MutableList<PumpHistoryEntry> = mutableListOf()
        val map: MutableMap<String?, PumpHistoryEntry?> = HashMap()
        for (pumpHistoryEntry in tbrsInput) {
            if (map.containsKey(pumpHistoryEntry.dt)) {
                decoder.decodeTempBasal(map[pumpHistoryEntry.dt]!!, pumpHistoryEntry)
                pumpHistoryEntry.setEntryType(medtronicUtil.medtronicPumpModel, PumpHistoryEntryType.TempBasalCombined)
                tbrs.add(pumpHistoryEntry)
                map.remove(pumpHistoryEntry.dt)
            } else {
                map[pumpHistoryEntry.dt] = pumpHistoryEntry
            }
        }
        return tbrs
    }

    private fun sort(list: MutableList<PumpHistoryEntry>) {
        // if (list != null && !list.isEmpty()) {
        //     Collections.sort(list, PumpHistoryEntry.Comparator())
        // }
        list.sortWith(PumpHistoryEntry.Comparator())
    }
}
