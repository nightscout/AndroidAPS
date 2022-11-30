package info.nightscout.androidaps.plugins.pump.medtronic.data

import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.interfaces.ui.UiInteraction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.lang.reflect.Type

@Suppress("UNCHECKED_CAST")
class MedtronicHistoryDataUTest : TestBase() {

    @Mock lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setUp() {
        medtronicUtil = MedtronicUtil(aapsLogger, rxBus, rileyLinkUtil, medtronicPumpStatus, uiInteraction)
        decoder = MedtronicPumpHistoryDecoder(aapsLogger, medtronicUtil, byteUtil)
    }

    @Test
    fun createTBRProcessList() {

        val unitToTest = MedtronicHistoryData(
            packetInjector, aapsLogger, sp, rh, rxBus, activePlugin,
            medtronicUtil, decoder, medtronicPumpStatus, pumpSync, pumpSyncStorage, uiInteraction
        )

        val gson = Gson()

        val fileText = ClassLoader.getSystemResource("tbr_data.json").readText()

        val listType: Type = object : TypeToken<MutableList<PumpHistoryEntry?>?>() {}.type
        val yourClassList: MutableList<PumpHistoryEntry> = gson.fromJson(fileText, listType)

        for (pumpHistoryEntry in yourClassList) {
            val stringObject = pumpHistoryEntry.decodedData["Object"] as LinkedTreeMap<String, Any>

            val rate: Double = stringObject["insulinRate"] as Double
            val durationMinutes: Double = stringObject["durationMinutes"] as Double
            val durationMinutesInt: Int = durationMinutes.toInt()

            val tmbPair = TempBasalPair(rate, false, durationMinutesInt)

            pumpHistoryEntry.decodedData.remove("Object")
            pumpHistoryEntry.addDecodedData("Object", tmbPair)
        }

        println("TBR Pre-Process List: " + gson.toJson(yourClassList))

        val createTBRProcessList = unitToTest.createTBRProcessList(yourClassList)

        println("TBR Process List: " + createTBRProcessList.size)

        for (tempBasalProcessDTO in createTBRProcessList) {
            println(tempBasalProcessDTO.toTreatmentString())
        }

    }

    @Test
    fun createTBRProcessList_SpecialCase() {

        val unitToTest = MedtronicHistoryData(
            packetInjector, aapsLogger, sp, rh, rxBus, activePlugin,
            medtronicUtil, decoder,
            medtronicPumpStatus,
            pumpSync,
            pumpSyncStorage, uiInteraction
        )

        val gson = Gson()

        val fileText = ClassLoader.getSystemResource("tbr_data_special.json").readText()

        val listType: Type = object : TypeToken<MutableList<PumpHistoryEntry?>?>() {}.type
        val yourClassList: MutableList<PumpHistoryEntry> = gson.fromJson(fileText, listType)

        for (pumpHistoryEntry in yourClassList) {
            val stringObject = pumpHistoryEntry.decodedData["Object"] as LinkedTreeMap<String, Any>

            val rate: Double = stringObject["insulinRate"] as Double
            val durationMinutes: Double = stringObject["durationMinutes"] as Double
            val durationMinutesInt: Int = durationMinutes.toInt()

            val tmbPair = TempBasalPair(rate, false, durationMinutesInt)

            pumpHistoryEntry.decodedData.remove("Object")
            pumpHistoryEntry.addDecodedData("Object", tmbPair)
        }

        println("TBR Pre-Process List (Special): " + gson.toJson(yourClassList))

        val createTBRProcessList = unitToTest.createTBRProcessList(yourClassList)

        println("TBR Process List (Special): " + createTBRProcessList.size)

        for (tempBasalProcessDTO in createTBRProcessList) {
            println(tempBasalProcessDTO.toTreatmentString())
        }

    }

}
