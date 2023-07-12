package info.nightscout.androidaps.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import info.nightscout.androidaps.database.TABLE_GLUCOSE_VALUES
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.interfaces.DBEntryWithTime
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import java.util.*

@Entity(
    tableName = TABLE_GLUCOSE_VALUES,
    foreignKeys = [ForeignKey(
        entity = GlucoseValue::class,
        parentColumns = ["id"],
        childColumns = ["referenceId"]
    )],
    indices = [
        Index("id"),
        Index("nightscoutId"),
        Index("sourceSensor"),
        Index("referenceId"),
        Index("timestamp")
    ]
)
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
        isValid == other.isValid &&
            timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            raw == other.raw &&
            value == other.value &&
            trendArrow == other.trendArrow &&
            noise == other.noise &&
            sourceSensor == other.sourceSensor

    fun onlyNsIdAdded(previous: GlucoseValue): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.interfaceIDs.nightscoutId == null &&
            interfaceIDs.nightscoutId != null

    fun isRecordDeleted(other: GlucoseValue): Boolean =
        isValid && !other.isValid

    enum class TrendArrow(val text: String, val symbol: String) {
        NONE("NONE", "??"),
        TRIPLE_UP("TripleUp", "X"),
        DOUBLE_UP("DoubleUp", "\u21c8"),
        SINGLE_UP("SingleUp", "\u2191"),
        FORTY_FIVE_UP("FortyFiveUp", "\u2197"),
        FLAT("Flat", "\u2192"),
        FORTY_FIVE_DOWN("FortyFiveDown", "\u2198"),
        SINGLE_DOWN("SingleDown", "\u2193"),
        DOUBLE_DOWN("DoubleDown", "\u21ca"),
        TRIPLE_DOWN("TripleDown", "X")
        ;

        companion object {

            fun fromString(direction: String?) =
                values().firstOrNull { it.text == direction } ?: NONE
        }
    }

    enum class SourceSensor(val text: String) {
        DEXCOM_NATIVE_UNKNOWN("AndroidAPS-Dexcom"),
        DEXCOM_G6_NATIVE("AndroidAPS-DexcomG6"),
        DEXCOM_G5_NATIVE("AndroidAPS-DexcomG5"),
        DEXCOM_G4_WIXEL("Bluetooth Wixel"),
        DEXCOM_G4_XBRIDGE("xBridge Wixel"),
        DEXCOM_G4_NATIVE("G4 Share Receiver"),
        MEDTRUM_A6("Medtrum A6"),
        DEXCOM_G4_NET("Network G4"),
        DEXCOM_G4_NET_XBRIDGE("Network G4 and xBridge"),
        DEXCOM_G4_NET_CLASSIC("Network G4 and Classic xDrip"),
        DEXCOM_G5_XDRIP("DexcomG5"),
        DEXCOM_G6_NATIVE_XDRIP("G6 Native"),
        DEXCOM_G5_NATIVE_XDRIP("G5 Native"),
        DEXCOM_G6_G5_NATIVE_XDRIP("G6 Native / G5 Native"),
        LIBRE_1_NET("Network libre"),
        LIBRE_1_BLUE("BlueReader"),
        LIBRE_1_PL("Transmiter PL"),
        LIBRE_1_BLUCON("Blucon"),
        LIBRE_1_TOMATO("Tomato"),
        LIBRE_1_RF("Rfduino"),
        LIBRE_1_LIMITTER("LimiTTer"),
        GLIMP("Glimp"),
        LIBRE_2_NATIVE("Libre2"),
        POCTECH_NATIVE("Poctech"),
        GLUNOVO_NATIVE("Glunovo"),
        MM_600_SERIES("MM600Series"),
        EVERSENSE("Eversense"),
        AIDEX("GlucoRx Aidex"),
        SIApp("SI App"),
        SinoApp("SI App"),
        RANDOM("Random"),
        UNKNOWN("Unknown"),

        IOB_PREDICTION("IOBPrediction"),
        A_COB_PREDICTION("aCOBPrediction"),
        COB_PREDICTION("COBPrediction"),
        UAM_PREDICTION("UAMPrediction"),
        ZT_PREDICTION("ZTPrediction"),
        ;

        companion object {

            fun fromString(source: String?) = values().firstOrNull { it.text == source } ?: UNKNOWN
        }
    }
}
