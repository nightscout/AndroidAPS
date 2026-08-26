package app.aaps.pump.carelevo.data.mapper

import app.aaps.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class CarelevoPatchInfoMapperTest {

    // Canonical ISO-8601 strings (Joda DateTime.toString() form: yyyy-MM-ddTHH:mm:ss.SSSZZ).
    // Using canonical form makes the entity round-trip (parse -> toString) exact.
    private val createdAtStr = "2024-01-15T10:30:00.000+02:00"
    private val updatedAtStr = "2024-02-20T08:15:30.500+02:00"

    private fun fullEntity() = CarelevoPatchInfoEntity(
        address = "AA:BB:CC:DD:EE:FF",
        createdAt = createdAtStr,
        updatedAt = updatedAtStr,
        manufactureNumber = "MFG-123",
        firmwareVersion = "1.2.3",
        bootDateTime = "2024-01-15T09:00:00",
        bootDateTimeUtcMillis = 1705309200000L,
        modelName = "CareLevo-X",
        insulinAmount = 300,
        insulinRemain = 145.5,
        thresholdInsulinRemain = 20,
        thresholdExpiry = 116,
        thresholdMaxBasalSpeed = 2.5,
        thresholdMaxBolusDose = 15.0,
        checkSafety = true,
        checkNeedle = false,
        needleFailedCount = 3,
        isConnected = true,
        needDiscard = false,
        isDiscard = true,
        isExtended = false,
        isValid = true,
        isStopped = false,
        stopMinutes = 42,
        stopMode = 2,
        isForceStopped = true,
        runningMinutes = 1234,
        infusedTotalBasalAmount = 12.34,
        infusedTotalBolusAmount = 56.78,
        pumpState = 5,
        mode = 1,
        bolusActionSeq = 7
    )

    private fun fullDomain() = CarelevoPatchInfoDomainModel(
        address = "AA:BB:CC:DD:EE:FF",
        createdAt = DateTime.parse(createdAtStr),
        updatedAt = DateTime.parse(updatedAtStr),
        manufactureNumber = "MFG-123",
        firmwareVersion = "1.2.3",
        bootDateTime = "2024-01-15T09:00:00",
        bootDateTimeUtcMillis = 1705309200000L,
        modelName = "CareLevo-X",
        insulinAmount = 300,
        insulinRemain = 145.5,
        thresholdInsulinRemain = 20,
        thresholdExpiry = 116,
        thresholdMaxBasalSpeed = 2.5,
        thresholdMaxBolusDose = 15.0,
        checkSafety = true,
        checkNeedle = false,
        needleFailedCount = 3,
        isConnected = true,
        needDiscard = false,
        isDiscard = true,
        isExtended = false,
        isValid = true,
        isStopped = false,
        stopMinutes = 42,
        stopMode = 2,
        isForceStopped = true,
        runningMinutes = 1234,
        infusedTotalBasalAmount = 12.34,
        infusedTotalBolusAmount = 56.78,
        pumpState = 5,
        mode = 1,
        bolusActionSeq = 7
    )

    // ---------- Entity -> Domain ----------

    @Test
    fun `entity to domain maps every scalar field`() {
        val d = fullEntity().transformToCarelevoPatchInfoDomainModel()
        assertThat(d.address).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(d.manufactureNumber).isEqualTo("MFG-123")
        assertThat(d.firmwareVersion).isEqualTo("1.2.3")
        assertThat(d.bootDateTime).isEqualTo("2024-01-15T09:00:00")
        assertThat(d.bootDateTimeUtcMillis).isEqualTo(1705309200000L)
        assertThat(d.modelName).isEqualTo("CareLevo-X")
        assertThat(d.insulinAmount).isEqualTo(300)
        assertThat(d.insulinRemain).isEqualTo(145.5)
        assertThat(d.thresholdInsulinRemain).isEqualTo(20)
        assertThat(d.thresholdExpiry).isEqualTo(116)
        assertThat(d.thresholdMaxBasalSpeed).isEqualTo(2.5)
        assertThat(d.thresholdMaxBolusDose).isEqualTo(15.0)
        assertThat(d.checkSafety).isTrue()
        assertThat(d.checkNeedle).isFalse()
        assertThat(d.needleFailedCount).isEqualTo(3)
        assertThat(d.isConnected).isTrue()
        assertThat(d.needDiscard).isFalse()
        assertThat(d.isDiscard).isTrue()
        assertThat(d.isExtended).isFalse()
        assertThat(d.isValid).isTrue()
        assertThat(d.isStopped).isFalse()
        assertThat(d.stopMinutes).isEqualTo(42)
        assertThat(d.stopMode).isEqualTo(2)
        assertThat(d.isForceStopped).isTrue()
        assertThat(d.runningMinutes).isEqualTo(1234)
        assertThat(d.infusedTotalBasalAmount).isEqualTo(12.34)
        assertThat(d.infusedTotalBolusAmount).isEqualTo(56.78)
        assertThat(d.pumpState).isEqualTo(5)
        assertThat(d.mode).isEqualTo(1)
        assertThat(d.bolusActionSeq).isEqualTo(7)
    }

    @Test
    fun `entity to domain parses createdAt and updatedAt strings`() {
        val d = fullEntity().transformToCarelevoPatchInfoDomainModel()
        assertThat(d.createdAt).isEqualTo(DateTime.parse(createdAtStr))
        assertThat(d.updatedAt).isEqualTo(DateTime.parse(updatedAtStr))
        // Instant preserved regardless of chronology.
        assertThat(d.createdAt.isEqual(DateTime.parse(createdAtStr))).isTrue()
    }

    @Test
    fun `entity to domain preserves all null optionals`() {
        val minimal = CarelevoPatchInfoEntity(
            address = "addr",
            createdAt = createdAtStr,
            updatedAt = updatedAtStr
        )
        val d = minimal.transformToCarelevoPatchInfoDomainModel()
        assertThat(d.address).isEqualTo("addr")
        assertThat(d.manufactureNumber).isNull()
        assertThat(d.firmwareVersion).isNull()
        assertThat(d.bootDateTime).isNull()
        assertThat(d.bootDateTimeUtcMillis).isNull()
        assertThat(d.modelName).isNull()
        assertThat(d.insulinAmount).isNull()
        assertThat(d.insulinRemain).isNull()
        assertThat(d.thresholdInsulinRemain).isNull()
        assertThat(d.thresholdExpiry).isNull()
        assertThat(d.thresholdMaxBasalSpeed).isNull()
        assertThat(d.thresholdMaxBolusDose).isNull()
        assertThat(d.checkSafety).isNull()
        assertThat(d.checkNeedle).isNull()
        assertThat(d.needleFailedCount).isNull()
        assertThat(d.isConnected).isNull()
        assertThat(d.needDiscard).isNull()
        assertThat(d.isDiscard).isNull()
        assertThat(d.isExtended).isNull()
        assertThat(d.isValid).isNull()
        assertThat(d.isStopped).isNull()
        assertThat(d.stopMinutes).isNull()
        assertThat(d.stopMode).isNull()
        assertThat(d.isForceStopped).isNull()
        assertThat(d.runningMinutes).isNull()
        assertThat(d.infusedTotalBasalAmount).isNull()
        assertThat(d.infusedTotalBolusAmount).isNull()
        assertThat(d.pumpState).isNull()
        assertThat(d.mode).isNull()
        assertThat(d.bolusActionSeq).isNull()
    }

    @Test
    fun `entity to domain throws on unparseable createdAt`() {
        val bad = CarelevoPatchInfoEntity(address = "addr", createdAt = "not-a-date", updatedAt = updatedAtStr)
        assertFailsWith<IllegalArgumentException> { bad.transformToCarelevoPatchInfoDomainModel() }
    }

    @Test
    fun `entity to domain throws on unparseable updatedAt`() {
        val bad = CarelevoPatchInfoEntity(address = "addr", createdAt = createdAtStr, updatedAt = "garbage")
        assertFailsWith<IllegalArgumentException> { bad.transformToCarelevoPatchInfoDomainModel() }
    }

    // ---------- Domain -> Entity ----------

    @Test
    fun `domain to entity maps every scalar field`() {
        val e = fullDomain().transformToCarelevoPatchInfoEntity()
        assertThat(e.address).isEqualTo("AA:BB:CC:DD:EE:FF")
        assertThat(e.manufactureNumber).isEqualTo("MFG-123")
        assertThat(e.firmwareVersion).isEqualTo("1.2.3")
        assertThat(e.bootDateTime).isEqualTo("2024-01-15T09:00:00")
        assertThat(e.bootDateTimeUtcMillis).isEqualTo(1705309200000L)
        assertThat(e.modelName).isEqualTo("CareLevo-X")
        assertThat(e.insulinAmount).isEqualTo(300)
        assertThat(e.insulinRemain).isEqualTo(145.5)
        assertThat(e.thresholdInsulinRemain).isEqualTo(20)
        assertThat(e.thresholdExpiry).isEqualTo(116)
        assertThat(e.thresholdMaxBasalSpeed).isEqualTo(2.5)
        assertThat(e.thresholdMaxBolusDose).isEqualTo(15.0)
        assertThat(e.checkSafety).isTrue()
        assertThat(e.checkNeedle).isFalse()
        assertThat(e.needleFailedCount).isEqualTo(3)
        assertThat(e.isConnected).isTrue()
        assertThat(e.needDiscard).isFalse()
        assertThat(e.isDiscard).isTrue()
        assertThat(e.isExtended).isFalse()
        assertThat(e.isValid).isTrue()
        assertThat(e.isStopped).isFalse()
        assertThat(e.stopMinutes).isEqualTo(42)
        assertThat(e.stopMode).isEqualTo(2)
        assertThat(e.isForceStopped).isTrue()
        assertThat(e.runningMinutes).isEqualTo(1234)
        assertThat(e.infusedTotalBasalAmount).isEqualTo(12.34)
        assertThat(e.infusedTotalBolusAmount).isEqualTo(56.78)
        assertThat(e.pumpState).isEqualTo(5)
        assertThat(e.mode).isEqualTo(1)
        assertThat(e.bolusActionSeq).isEqualTo(7)
    }

    @Test
    fun `domain to entity serializes dates via toString`() {
        val domain = fullDomain()
        val e = domain.transformToCarelevoPatchInfoEntity()
        assertThat(e.createdAt).isEqualTo(domain.createdAt.toString())
        assertThat(e.updatedAt).isEqualTo(domain.updatedAt.toString())
        // Canonical inputs remain canonical after serialization.
        assertThat(e.createdAt).isEqualTo(createdAtStr)
        assertThat(e.updatedAt).isEqualTo(updatedAtStr)
    }

    @Test
    fun `domain to entity preserves all null optionals`() {
        val minimal = CarelevoPatchInfoDomainModel(
            address = "addr",
            createdAt = DateTime.parse(createdAtStr),
            updatedAt = DateTime.parse(updatedAtStr)
        )
        val e = minimal.transformToCarelevoPatchInfoEntity()
        assertThat(e.address).isEqualTo("addr")
        assertThat(e.manufactureNumber).isNull()
        assertThat(e.firmwareVersion).isNull()
        assertThat(e.bootDateTime).isNull()
        assertThat(e.bootDateTimeUtcMillis).isNull()
        assertThat(e.modelName).isNull()
        assertThat(e.insulinAmount).isNull()
        assertThat(e.insulinRemain).isNull()
        assertThat(e.thresholdInsulinRemain).isNull()
        assertThat(e.thresholdExpiry).isNull()
        assertThat(e.thresholdMaxBasalSpeed).isNull()
        assertThat(e.thresholdMaxBolusDose).isNull()
        assertThat(e.checkSafety).isNull()
        assertThat(e.checkNeedle).isNull()
        assertThat(e.needleFailedCount).isNull()
        assertThat(e.isConnected).isNull()
        assertThat(e.needDiscard).isNull()
        assertThat(e.isDiscard).isNull()
        assertThat(e.isExtended).isNull()
        assertThat(e.isValid).isNull()
        assertThat(e.isStopped).isNull()
        assertThat(e.stopMinutes).isNull()
        assertThat(e.stopMode).isNull()
        assertThat(e.isForceStopped).isNull()
        assertThat(e.runningMinutes).isNull()
        assertThat(e.infusedTotalBasalAmount).isNull()
        assertThat(e.infusedTotalBolusAmount).isNull()
        assertThat(e.pumpState).isNull()
        assertThat(e.mode).isNull()
        assertThat(e.bolusActionSeq).isNull()
    }

    // ---------- Round trips ----------

    @Test
    fun `round trip domain to entity to domain preserves values`() {
        val original = fullDomain()
        val result = original.transformToCarelevoPatchInfoEntity().transformToCarelevoPatchInfoDomainModel()
        // Instant preserved (chronology/zone may normalize to fixed offset).
        assertThat(result.createdAt.isEqual(original.createdAt)).isTrue()
        assertThat(result.updatedAt.isEqual(original.updatedAt)).isTrue()
        // All non-date fields unchanged -> compare after normalizing the dates away.
        assertThat(result.copy(createdAt = original.createdAt, updatedAt = original.updatedAt)).isEqualTo(original)
    }

    @Test
    fun `round trip entity to domain to entity preserves canonical strings and values`() {
        val original = fullEntity()
        val result = original.transformToCarelevoPatchInfoDomainModel().transformToCarelevoPatchInfoEntity()
        // Canonical date strings survive the parse -> toString cycle exactly.
        assertThat(result).isEqualTo(original)
    }

    @Test
    fun `round trip domain to entity to domain preserves null optionals`() {
        val original = CarelevoPatchInfoDomainModel(
            address = "addr",
            createdAt = DateTime.parse(createdAtStr),
            updatedAt = DateTime.parse(updatedAtStr)
        )
        val result = original.transformToCarelevoPatchInfoEntity().transformToCarelevoPatchInfoDomainModel()
        assertThat(result.copy(createdAt = original.createdAt, updatedAt = original.updatedAt)).isEqualTo(original)
    }

    @Test
    fun `boolean flags map with inverted pattern`() {
        val e = fullEntity().copy(
            checkSafety = false,
            checkNeedle = true,
            isConnected = false,
            needDiscard = true,
            isDiscard = false,
            isExtended = true,
            isValid = false,
            isStopped = true,
            isForceStopped = false
        )
        val d = e.transformToCarelevoPatchInfoDomainModel()
        assertThat(d.checkSafety).isFalse()
        assertThat(d.checkNeedle).isTrue()
        assertThat(d.isConnected).isFalse()
        assertThat(d.needDiscard).isTrue()
        assertThat(d.isDiscard).isFalse()
        assertThat(d.isExtended).isTrue()
        assertThat(d.isValid).isFalse()
        assertThat(d.isStopped).isTrue()
        assertThat(d.isForceStopped).isFalse()
    }

    @Test
    fun `negative and zero numeric values pass through unchanged`() {
        val e = fullEntity().copy(
            insulinAmount = 0,
            insulinRemain = 0.0,
            needleFailedCount = -1,
            stopMinutes = 0,
            stopMode = -5,
            runningMinutes = 0,
            infusedTotalBasalAmount = -0.5,
            infusedTotalBolusAmount = 0.0,
            pumpState = 0,
            mode = -1,
            bolusActionSeq = 0
        )
        val d = e.transformToCarelevoPatchInfoDomainModel()
        assertThat(d.insulinAmount).isEqualTo(0)
        assertThat(d.insulinRemain).isEqualTo(0.0)
        assertThat(d.needleFailedCount).isEqualTo(-1)
        assertThat(d.stopMinutes).isEqualTo(0)
        assertThat(d.stopMode).isEqualTo(-5)
        assertThat(d.runningMinutes).isEqualTo(0)
        assertThat(d.infusedTotalBasalAmount).isEqualTo(-0.5)
        assertThat(d.infusedTotalBolusAmount).isEqualTo(0.0)
        assertThat(d.pumpState).isEqualTo(0)
        assertThat(d.mode).isEqualTo(-1)
        assertThat(d.bolusActionSeq).isEqualTo(0)
    }
}
