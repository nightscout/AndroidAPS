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
import org.junit.jupiter.api.BeforeEach
import org.mockito.Answers
import org.mockito.Mock

open class MedtronicTestBase : TestBaseWithProfile() {

    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var pumpSyncStorage: PumpSyncStorage

    @Mock lateinit var medtronicUtil: MedtronicUtil
    @Mock lateinit var decoder: MedtronicPumpHistoryDecoder
    lateinit var rileyLinkUtil: RileyLinkUtil

    @BeforeEach
    fun mock() {
        rileyLinkUtil = RileyLinkUtil(aapsLogger, context)
    }

    fun initializeCommonMocks() {
        // Common initialization for all Medtronic tests
        // Override in specific test classes if needed
    }

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
            val b = if (item > 128) item - 256 else item
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
