package info.nightscout.androidaps.plugins.pump.medtronic.data

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.utils.DateTimeUtil
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicTestBase
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.lang.reflect.Type

@Suppress("UNCHECKED_CAST")
class MedtronicHistoryDataUTest : MedtronicTestBase() {

    @Mock lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Mock lateinit var uiInteraction: UiInteraction

    @BeforeEach
    fun setUp() {
        medtronicUtil = MedtronicUtil(aapsLogger, rxBus, rileyLinkUtil, medtronicPumpStatus, uiInteraction)
        `when`(medtronicUtil.medtronicPumpModel).thenReturn(MedtronicDeviceType.Medtronic_723_Revel)
        decoder = MedtronicPumpHistoryDecoder(aapsLogger, medtronicUtil)
    }

    @Test
    fun createTBRProcessList() {

        val unitToTest = MedtronicHistoryData(
            packetInjector, aapsLogger, sp, rh, rxBus, activePlugin,
            medtronicUtil, decoder, medtronicPumpStatus, pumpSync, pumpSyncStorage, uiInteraction, profileUtil
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
            pumpSyncStorage, uiInteraction, profileUtil
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

    @Test
    fun processBgReceived_WithMgdl() {

        val unitToTest = MedtronicHistoryData(
            packetInjector, aapsLogger, sp, rh, rxBus, activePlugin,
            medtronicUtil, decoder,
            medtronicPumpStatus,
            pumpSync,
            pumpSyncStorage, uiInteraction, profileUtil
        )

        val glucoseMgdl = 175

        `when`(sp.getString(app.aaps.core.utils.R.string.key_units, GlucoseUnit.MGDL.asText)).thenReturn(GlucoseUnit.MGDL.asText)

        val bgRecord = PumpHistoryEntry()
        bgRecord.setEntryType(medtronicUtil.medtronicPumpModel, PumpHistoryEntryType.BGReceived)
        bgRecord.addDecodedData("GlucoseMgdl", glucoseMgdl)
        bgRecord.addDecodedData("MeterSerial", "123456")

        unitToTest.processBgReceived(listOf(bgRecord))

        verify(pumpSync).insertFingerBgIfNewWithTimestamp(
            DateTimeUtil.toMillisFromATD(bgRecord.atechDateTime),
            glucoseMgdl.toDouble(),
            GlucoseUnit.MGDL, null,
            bgRecord.pumpId,
            medtronicPumpStatus.pumpType,
            medtronicPumpStatus.serialNumber
        )

    }

    @Test
    fun processBgReceived_WithMmol() {

        val unitToTest = MedtronicHistoryData(
            packetInjector, aapsLogger, sp, rh, rxBus, activePlugin,
            medtronicUtil, decoder,
            medtronicPumpStatus,
            pumpSync,
            pumpSyncStorage, uiInteraction, profileUtil
        )
        val glucoseMgdl = 180
        val glucoseMmol = 10.0

        `when`(sp.getString(app.aaps.core.utils.R.string.key_units, GlucoseUnit.MGDL.asText)).thenReturn(GlucoseUnit.MMOL.asText)

        val bgRecord = PumpHistoryEntry()
        bgRecord.setEntryType(medtronicUtil.medtronicPumpModel, PumpHistoryEntryType.BGReceived)
        bgRecord.addDecodedData("GlucoseMgdl", glucoseMgdl)
        bgRecord.addDecodedData("MeterSerial", "123456")

        unitToTest.processBgReceived(listOf(bgRecord))

        verify(pumpSync).insertFingerBgIfNewWithTimestamp(
            DateTimeUtil.toMillisFromATD(bgRecord.atechDateTime),
            glucoseMmol,
            GlucoseUnit.MMOL, null,
            bgRecord.pumpId,
            medtronicPumpStatus.pumpType,
            medtronicPumpStatus.serialNumber
        )

    }

}
