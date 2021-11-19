package info.nightscout.androidaps.plugins.pump.medtronic.data

import java.lang.reflect.Type
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.internal.LinkedTreeMap
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.common.sync.PumpSyncStorage
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.hamcrest.Matchers.notNullValue
import org.junit.Assert.*

import org.junit.Test
import org.mockito.Mock
import java.io.File
import java.net.URL

class MedtronicHistoryDataUTest : TestBase() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var medtronicUtil: MedtronicUtil
    @Mock lateinit var medtronicPumpHistoryDecoder: MedtronicPumpHistoryDecoder
    @Mock lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var pumpSyncStorage: PumpSyncStorage
    @Mock lateinit var sp: SP


    private val packetInjector = HasAndroidInjector {
        AndroidInjector {

        }
    }



    @Test
    fun createTBRProcessList() {

        var unitToTest = MedtronicHistoryData(packetInjector, aapsLogger, sp, activePlugin,
        medtronicUtil,   medtronicPumpHistoryDecoder,
        medtronicPumpStatus,
        pumpSync,
        pumpSyncStorage)


        val gson = Gson()

        val fileText = ClassLoader.getSystemResource("tbr_data.json").readText()

        val listType: Type = object : TypeToken<MutableList<PumpHistoryEntry?>?>() {}.getType()
        val yourClassList: MutableList<PumpHistoryEntry> = gson.fromJson(fileText, listType)

        for (pumpHistoryEntry in yourClassList) {
            val stringObject = pumpHistoryEntry.decodedData["Object"] as LinkedTreeMap<String,Object>

            val rate : Double = stringObject.get("insulinRate") as Double
            val durationMinutes: Double = stringObject.get("durationMinutes") as Double
            val durationMinutesInt : Int = durationMinutes.toInt()

            var  tmbPair = TempBasalPair(rate, false, durationMinutesInt)

            pumpHistoryEntry.decodedData.remove("Object")
            pumpHistoryEntry.addDecodedData("Object", tmbPair)
        }

        System.out.println("TBR Pre-Process List: " + gson.toJson(yourClassList))

        val createTBRProcessList = unitToTest.createTBRProcessList(yourClassList)

        System.out.println("TBR Process List: " + createTBRProcessList.size)

        for (tempBasalProcessDTO in createTBRProcessList) {
            System.out.println(tempBasalProcessDTO.toTreatmentString())
        }

    }


}