package info.nightscout.androidaps

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
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.TestAapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLoggerTest
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.Locale

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
open class TestBase {

    val aapsLogger = AAPSLoggerTest()
    val aapsSchedulers: AapsSchedulers = TestAapsSchedulers()
    var rxBus: RxBus = RxBus(TestAapsSchedulers(), aapsLogger)
    var byteUtil = ByteUtil()
    var rileyLinkUtil = RileyLinkUtil()

    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var pumpSyncStorage: PumpSyncStorage
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) lateinit var activePlugin: ActivePlugin
    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper

    lateinit var medtronicUtil : MedtronicUtil
    lateinit var decoder : MedtronicPumpHistoryDecoder


    val packetInjector = HasAndroidInjector {
        AndroidInjector {

        }
    }

    @BeforeEach
    fun setupLocale() {
        Locale.setDefault(Locale.ENGLISH)
        System.setProperty("disableFirebase", "true")
    }

    // Workaround for Kotlin nullability.
    // https://medium.com/@elye.project/befriending-kotlin-and-mockito-1c2e7b0ef791
    // https://stackoverflow.com/questions/30305217/is-it-possible-to-use-mockito-in-kotlin
    fun <T> anyObject(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    fun preProcessListTBR(inputList: MutableList<PumpHistoryEntry>) {

        var tbrs: MutableList<PumpHistoryEntry> = mutableListOf()

        for (pumpHistoryEntry in inputList) {
            if (pumpHistoryEntry.entryType === PumpHistoryEntryType.TempBasalRate ||
                pumpHistoryEntry.entryType === PumpHistoryEntryType.TempBasalDuration) {
                tbrs.add(pumpHistoryEntry)
            }
        }

        inputList.removeAll(tbrs)

        inputList.addAll(preProcessTBRs(tbrs))

        sort(inputList)

        //return inputList

    }


    private fun preProcessTBRs(TBRs_Input: MutableList<PumpHistoryEntry>): MutableList<PumpHistoryEntry> {
        val tbrs: MutableList<PumpHistoryEntry> = mutableListOf()
        val map: MutableMap<String?, PumpHistoryEntry?> = HashMap()
        for (pumpHistoryEntry in TBRs_Input) {
            if (map.containsKey(pumpHistoryEntry.DT)) {
                decoder.decodeTempBasal(map[pumpHistoryEntry.DT]!!, pumpHistoryEntry)
                pumpHistoryEntry.setEntryType(medtronicUtil.medtronicPumpModel, PumpHistoryEntryType.TempBasalCombined)
                tbrs.add(pumpHistoryEntry)
                map.remove(pumpHistoryEntry.DT)
            } else {
                map[pumpHistoryEntry.DT] = pumpHistoryEntry
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


    @Suppress("Unchecked_Cast")
    fun <T> uninitialized(): T = null as T
}