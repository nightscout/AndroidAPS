package app.aaps.cgm.dexcomg7.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Vectors from Trio's G7SensorKit tests (G7GlucoseMessageTests.swift), captured from real sensors. */
class G7MessagesTest {

    private fun glucose(hex: String) = requireNotNull(G7GlucoseMessage.parse(hex.hexToBytes()))

    @Test
    fun readsAReading() {
        val message = glucose("4e00c35501002601000106008a00060187000f")
        assertThat(message.glucose).isEqualTo(138)
        assertThat(message.glucoseTimestamp).isEqualTo(87485)
        assertThat(message.glucoseIsDisplayOnly).isFalse()
    }

    @Test
    fun readsACalibrationAsDisplayOnly() {
        val message = glucose("4e000ec10d00c00b00010000680006fe63001f")
        assertThat(message.glucose).isEqualTo(104)
        assertThat(message.glucoseTimestamp).isEqualTo(901390)
        assertThat(message.glucoseIsDisplayOnly).isTrue()
    }

    @Test
    fun followsASensorStart() {
        val messages = listOf(
            "4e00b6000000010000006600ffff017fffff00",
            "4e00cd000000030000010500ffff027fffff01",
            "4e00f90100000400000105009100027effff02",
            "4e00250300000500000105007d00027effff02",
            "4e0051040000060000010500650002dfffff02",
            "4e007d0500000700000105004e0002e7ffff02",
            "4e00ab060000080000010700540006f5ffff0e",
            "4e00d507000009000001050061000601ffff0e",
            "4e004d440e00d40b0001d46b650018036a000e"
        ).map { glucose(it) }

        assertThat(messages[0].glucose).isNull()
        assertThat(messages[1].glucose).isNull()
        assertThat(messages[2].glucose).isEqualTo(145)

        assertThat(messages[0].algorithmState.state).isEqualTo(AlgorithmState.State.STOPPED)
        for (i in 1..5) assertThat(messages[i].algorithmState.state).isEqualTo(AlgorithmState.State.WARMUP)
        assertThat(messages[6].algorithmState.state).isEqualTo(AlgorithmState.State.OK)
        assertThat(messages[7].algorithmState.state).isEqualTo(AlgorithmState.State.OK)
        assertThat(messages[8].algorithmState.state).isEqualTo(AlgorithmState.State.EXPIRED)

        assertThat(messages.map { it.sequence }).containsExactly(1, 3, 4, 5, 6, 7, 8, 9, 3028).inOrder()
        assertThat(messages.map { it.glucoseTimestamp }).containsExactly(80L, 200L, 500L, 800L, 1100L, 1400L, 1700L, 2000L, 907385L).inOrder()
    }

    @Test
    fun readsAllDetails() {
        val message = glucose("4e00a89c00008800000104008d0006038a000f")
        assertThat(message.glucose).isEqualTo(141)
        assertThat(message.glucoseTimestamp).isEqualTo(40100)
        assertThat(message.sequence).isEqualTo(136)
        assertThat(message.age).isEqualTo(4)
        assertThat(message.predicted).isEqualTo(138)
        assertThat(message.trend).isWithin(1e-9).of(0.3)
        assertThat(message.algorithmState.hasReliableGlucose).isTrue()
        assertThat(message.glucoseIsDisplayOnly).isFalse()
        assertThat(message.trendType).isEqualTo(G7Trend.FLAT)
    }

    @Test
    fun readsNegativeAndMissingRate() {
        assertThat(glucose("4e00c6cc0d00ca0b00010500610006fe5b000f").trend).isWithin(1e-9).of(-0.2)
        assertThat(glucose("4e00c6cc0d00ca0b000105006100067f5b000f").trend).isNull()
    }

    @Test
    fun readsTwoByteAge() {
        val message = glucose("4e00f9590200030200012a018f000610d9000f")
        assertThat(message.age).isEqualTo(298)
        assertThat(message.messageTimestamp).isEqualTo(154105)
        assertThat(message.glucoseTimestamp).isEqualTo(153807)

        val expired = glucose("4e004d440e00d40b0001d46b650018036a000e")
        assertThat(expired.age).isEqualTo(27604)
        assertThat(expired.messageTimestamp).isEqualTo(934989)
        assertThat(expired.glucoseTimestamp).isEqualTo(907385)
    }

    @Test
    fun refusesOtherMessages() {
        assertThat(G7GlucoseMessage.parse("4e01c35501002601000106008a00060187000f".hexToBytes())).isNull()
        assertThat(G7GlucoseMessage.parse("4e00c355".hexToBytes())).isNull()
        assertThat(G7GlucoseMessage.parse("5200c35501002601000106008a00060187000f".hexToBytes())).isNull()
    }

    @Test
    fun readsBackfill() {
        val message = requireNotNull(G7BackfillMessage.parse("cf5802008f00060f10".hexToBytes()))
        assertThat(message.timestamp).isEqualTo(153807)
        assertThat(message.glucose).isEqualTo(143)
        assertThat(message.hasReliableGlucose).isTrue()
        assertThat(message.glucoseIsDisplayOnly).isFalse()

        assertThat(G7BackfillMessage.parse("f20e0d00ba00060ffb".hexToBytes())!!.timestamp).isEqualTo(855794)
        assertThat(G7BackfillMessage.parse("f63d00008500061efe".hexToBytes())!!.glucoseIsDisplayOnly).isTrue()
    }

    @Test
    fun splitsBackfillFrames() {
        val frame = "cf5802008f00060f10f20e0d00ba00060ffb".hexToBytes()
        assertThat(G7BackfillMessage.parseFrame(frame)!!.map { it.timestamp }).containsExactly(153807L, 855794L).inOrder()
        assertThat(G7BackfillMessage.parseFrame("cf5802008f00060f".hexToBytes())).isNull()
    }

    @Test
    fun readsExtendedVersion() {
        val tenDay = requireNotNull(ExtendedVersionMessage.parse("5200c0d70d005406000204 04ff0c00".replace(" ", "").hexToBytes()))
        assertThat(tenDay.sessionLengthSeconds).isEqualTo(907200) // 10.5 days
        assertThat(tenDay.warmupSeconds).isEqualTo(1620) // 27 minutes
        assertThat(tenDay.maxLifetimeDays).isEqualTo(12)

        val fifteenDay = requireNotNull(ExtendedVersionMessage.parse("5200406f1400880e00010a04ff1100".hexToBytes()))
        assertThat(fifteenDay.sessionLengthSeconds).isEqualTo(1339200) // 15.5 days
        assertThat(fifteenDay.warmupSeconds).isEqualTo(3720)
    }

    @Test
    fun readsTransmitterVersion() {
        // 4a 00 | firmware 2.18.2.88 | software | silicon | serial 6 bytes LE
        val serial = 379013053518L
        val serialBytes = ByteArray(6) { (serial shr (8 * it)).toByte() }
        val data = "4a00".hexToBytes() + byteArrayOf(2, 18, 2, 88) + "2a340000".hexToBytes() + "01000000".hexToBytes() + serialBytes
        val message = requireNotNull(TransmitterVersionMessage.parse(data))
        assertThat(message.firmwareVersion).isEqualTo("2.18.2.88")
        assertThat(message.softwareNumber).isEqualTo(13354)
        assertThat(message.serialNumber).isEqualTo(serial)
    }

    @Test
    fun readsAuthStatus() {
        val ok = requireNotNull(AuthStatusMessage.parse("050101".hexToBytes()))
        assertThat(ok.isAuthenticated).isTrue()
        assertThat(ok.isBonded).isTrue()
        assertThat(ok.isRejected).isFalse()
        assertThat(ok.failureCode).isNull()

        val refused = requireNotNull(AuthStatusMessage.parse("050202".hexToBytes()))
        assertThat(refused.isRejected).isTrue()
        assertThat(refused.failureCode).isEqualTo(G7AuthFailureCode.DEVICE_TYPE_RESTRICTION)
    }

    @Test
    fun buildsCalibrationAndBackfillRequests() {
        assertThat(G7CalibrateTxMessage(145, 901259).data.toHex()).isEqualTo("349100" + "8bc00d00")
        assertThat(G7Commands.backfill(0x010203, 0x0a0b0c).toHex()).isEqualTo("59" + "03020100" + "0c0b0a00")
    }

    @Test
    fun readsCalibrationAnswers() {
        assertThat(G7CalibrateRxMessage.parse("34000100".hexToBytes())!!.accepted).isTrue()
        assertThat(G7CalibrateRxMessage.parse("34000500".hexToBytes())!!.accepted).isFalse()

        // lastBG=100, lastBGTime=901259, processing=completeHigh, permitted, display=phone
        val bounds = requireNotNull(
            G7CalibrationBoundsMessage.parse("32000141000000".hexToBytes() + "6400".hexToBytes() + "8bc00d00".hexToBytes() + "03010200000000".hexToBytes())
        )
        assertThat(bounds.lastGlucose).isEqualTo(100)
        assertThat(bounds.lastCalibrationTime).isEqualTo(901259)
        assertThat(bounds.processingStatus).isEqualTo(G7CalibrationProcessingStatus.COMPLETE_HIGH)
        assertThat(bounds.calibrationsPermitted).isTrue()
        assertThat(bounds.lastDisplayType).isEqualTo(G7DisplayType.PHONE)
    }

    @Test
    fun mapsRatesToTrends() {
        assertThat(G7Trend.fromRate(-3.5)).isEqualTo(G7Trend.DOUBLE_DOWN)
        assertThat(G7Trend.fromRate(-2.0)).isEqualTo(G7Trend.SINGLE_DOWN)
        assertThat(G7Trend.fromRate(-1.0)).isEqualTo(G7Trend.FORTY_FIVE_DOWN)
        assertThat(G7Trend.fromRate(0.9)).isEqualTo(G7Trend.FLAT)
        assertThat(G7Trend.fromRate(1.0)).isEqualTo(G7Trend.FORTY_FIVE_UP)
        assertThat(G7Trend.fromRate(2.5)).isEqualTo(G7Trend.SINGLE_UP)
        assertThat(G7Trend.fromRate(3.0)).isEqualTo(G7Trend.DOUBLE_UP)
        assertThat(G7Trend.fromRate(null)).isNull()
    }

    @Test
    fun knowsSensorModels() {
        assertThat(G7SensorModel.fromAdvertisedName("DXCM12")).isEqualTo(G7SensorModel.G7)
        assertThat(G7SensorModel.fromAdvertisedName("DX0212")).isEqualTo(G7SensorModel.ONE_PLUS)
        assertThat(G7SensorModel.fromAdvertisedName("DX0112")).isNull() // Stelo is not supported
        assertThat(G7SensorModel.isFamilyName("Dexcom12")).isTrue()
        assertThat(G7SensorModel.sameSensor("DXCM12", "Dexcom12")).isTrue()
        assertThat(G7SensorModel.sameSensor("DXCM12", "Dexcom13")).isFalse()
    }
}
