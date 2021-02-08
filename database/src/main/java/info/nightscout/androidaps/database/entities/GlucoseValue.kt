package info.nightscout.androidaps.database.entities

import com.google.gson.annotations.SerializedName
import androidx.room.*
import info.nightscout.androidaps.database.TABLE_GLUCOSE_VALUES
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTime
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.TimeZone

@Entity(tableName = TABLE_GLUCOSE_VALUES,
    foreignKeys = [ForeignKey(
        entity = GlucoseValue::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"])],
    indices = [Index("referenceId"), Index("timestamp")])
data class GlucoseValue(
    @PrimaryKey(autoGenerate = true)
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    @Embedded
    override var interfaceIDs_backing: InterfaceIDs? = InterfaceIDs(),
    override var timestamp: Long,
    override var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var raw: Double?,
    var value: Double,
    var trendArrow: TrendArrow,
    var noise: Double?,
    var sourceSensor: SourceSensor
) : TraceableDBEntry, DBEntryWithTime {

    fun contentEqualsTo(other: GlucoseValue): Boolean =
        timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            raw == other.raw &&
            value == other.value &&
            trendArrow == other.trendArrow &&
            noise == other.noise &&
            sourceSensor == other.sourceSensor &&
            isValid == other.isValid

    fun isRecordDeleted(other: GlucoseValue): Boolean =
        isValid && !other.isValid

    enum class TrendArrow (val text:String, val symbol:String){
        @SerializedName("NONE") NONE("NONE", "??"),
        @SerializedName("TripleUp")TRIPLE_UP("TripleUp", "X"),
        @SerializedName("DoubleUp")DOUBLE_UP("DoubleUp", "\u21c8"),
        @SerializedName("SingleUp")SINGLE_UP("SingleUp", "\u2191"),
        @SerializedName("FortyFiveUp")FORTY_FIVE_UP("FortyFiveUp", "\u2197"),
        @SerializedName("Flat")FLAT("Flat", "\u2192"),
        @SerializedName("FortyFiveDown")FORTY_FIVE_DOWN("FortyFiveDown", "\u2198"),
        @SerializedName("SingleDown")SINGLE_DOWN("SingleDown", "\u2193"),
        @SerializedName("DoubleDown")DOUBLE_DOWN("DoubleDown", "\u21ca"),
        @SerializedName("TripleDown")TRIPLE_DOWN("TripleDown", "X")
        ;

        companion object {
            fun fromString(direction : String?) = values().firstOrNull {it.text == direction} ?: NONE
        }
    }

    enum class SourceSensor(val text : String) {
        @SerializedName("AndroidAPS-Dexcom") DEXCOM_NATIVE_UNKNOWN("AndroidAPS-Dexcom"),
        @SerializedName("AndroidAPS-DexcomG6") DEXCOM_G6_NATIVE("AndroidAPS-DexcomG6"),
        @SerializedName("AndroidAPS-DexcomG5") DEXCOM_G5_NATIVE("AndroidAPS-DexcomG5"),
        @SerializedName("Bluetooth Wixel") DEXCOM_G4_WIXEL("Bluetooth Wixel"),
        @SerializedName("xBridge Wixel") DEXCOM_G4_XBRIDGE("xBridge Wixel"),
        @SerializedName("G4 Share Receiver") DEXCOM_G4_NATIVE("G4 Share Receiver"),
        @SerializedName("Medtrum A6") MEDTRUM_A6("Medtrum A6"),
        @SerializedName("Network G4") DEXCOM_G4_NET("Network G4"),
        @SerializedName("Network G4 and xBridge") DEXCOM_G4_NET_XBRIDGE("Network G4 and xBridge"),
        @SerializedName("Network G4 and Classic xDrip") DEXCOM_G4_NET_CLASSIC("Network G4 and Classic xDrip"),
        @SerializedName("DexcomG5") DEXCOM_G5_XDRIP("DexcomG5"),
        @SerializedName("G6 Native") DEXCOM_G6_NATIVE_XDRIP("G6 Native"),
        @SerializedName("G5 Native") DEXCOM_G5_NATIVE_XDRIP("G5 Native"),
        @SerializedName("Network libre") LIBRE_1_NET("Network libre"),
        @SerializedName("BlueReader") LIBRE_1_BLUE("BlueReader"),
        @SerializedName("Transmiter PL") LIBRE_1_PL("Transmiter PL"),
        @SerializedName("Blucon") LIBRE_1_BLUCON("Blucon"),
        @SerializedName("Tomato") LIBRE_1_TOMATO("Tomato"),
        @SerializedName("Rfduino") LIBRE_1_RF("Rfduino"),
        @SerializedName("LimiTTer") LIBRE_1_LIMITTER("LimiTTer"),
        @SerializedName("Glimp") GLIMP("Glimp"),
        @SerializedName("Libre2") LIBRE_2_NATIVE("Libre2"),
        @SerializedName("Poctech") POCTECH_NATIVE("Poctech"),
        @SerializedName("MM600Series") MM_600_SERIES("MM600Series"),
        @SerializedName("Eversense") EVERSENSE("Eversense"),
        @SerializedName("Random") RANDOM("Random"),
        @SerializedName("Unknown") UNKNOWN("Unknown"),

        @SerializedName("IOBPrediction") IOB_PREDICTION("IOBPrediction"),
        @SerializedName("aCOBPrediction") aCOB_PREDICTION("aCOBPrediction"),
        @SerializedName("COBPrediction") COB_PREDICTION("COBPrediction"),
        @SerializedName("UAMPrediction") UAM_PREDICTION("UAMPrediction"),
        @SerializedName("ZTPrediction") ZT_PREDICTION("ZTPrediction")
        ;

        companion object {

            fun fromString(source: String?) = values().firstOrNull { it.text == source } ?: UNKNOWN
        }
    }
}