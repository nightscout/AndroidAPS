package app.aaps.pump.medtronic

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.sync.PumpSyncStorage
import app.aaps.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import app.aaps.pump.medtronic.util.MedtronicUtil
import app.aaps.shared.tests.TestBaseWithProfile
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.mockito.Answers
import org.mockito.Mock

open class MedtronicTestBase : TestBaseWithProfile() {

    var rileyLinkUtil = RileyLinkUtil()

    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var pumpSyncStorage: PumpSyncStorage
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) override lateinit var activePlugin: ActivePlugin

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

    fun getPumpHistoryEntryFromData(vararg elements: Int): PumpHistoryEntry {
        val data: MutableList<Byte> = ArrayList()
        for (item in elements) {
            var b = if (item > 128) item - 256 else item
            data.add(b.toByte())
        }

        val entryType = PumpHistoryEntryType.getByCode(data[0])

        val phe = PumpHistoryEntry()
        phe.setEntryType(medtronicUtil.medtronicPumpModel, entryType)
        phe.setData(data, false)

        decoder.decodeRecord(phe)

        return phe
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
