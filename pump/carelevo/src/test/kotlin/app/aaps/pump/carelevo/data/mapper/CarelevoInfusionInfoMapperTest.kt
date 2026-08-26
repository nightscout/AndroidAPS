package app.aaps.pump.carelevo.data.mapper

import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalSegmentInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Test

/**
 * Pure-logic unit tests for the entity <-> domain mappers in `CarelevoInfusionInfoMapper.kt`.
 *
 * Timestamp handling: entity strings are canonical ISO-8601 in UTC so that a
 * `String -> DateTime.parse -> DateTime.toString -> String` round trip is stable and the produced
 * DateTime chronology matches a UTC-built DateTime (offset 0 resolves to DateTimeZone.UTC).
 */
internal class CarelevoInfusionInfoMapperTest {

    private val createdStr = "2026-07-16T12:30:45.123Z"
    private val updatedStr = "2026-07-16T13:31:46.456Z"
    private val createdDt = DateTime(2026, 7, 16, 12, 30, 45, 123, DateTimeZone.UTC)
    private val updatedDt = DateTime(2026, 7, 16, 13, 31, 46, 456, DateTimeZone.UTC)

    // region BasalSegment

    @Test
    fun `basal segment entity to domain maps every field`() {
        val entity = CarelevoBasalSegmentInfusionInfoEntity(
            createdAt = createdStr, updatedAt = updatedStr, startTime = 60, endTime = 120, speed = 1.25
        )
        val domain = entity.transformToCarelevoBasalSegmentInfusionInfoDomainModel()
        assertThat(domain.createdAt).isEqualTo(DateTime.parse(createdStr))
        assertThat(domain.updatedAt).isEqualTo(DateTime.parse(updatedStr))
        assertThat(domain.startTime).isEqualTo(60)
        assertThat(domain.endTime).isEqualTo(120)
        assertThat(domain.speed).isEqualTo(1.25)
    }

    @Test
    fun `basal segment domain to entity maps every field and serializes timestamps`() {
        val domain = CarelevoBasalSegmentInfusionInfoDomainModel(
            createdAt = createdDt, updatedAt = updatedDt, startTime = 0, endTime = 30, speed = 0.5
        )
        val entity = domain.transformToCarelevoBasalSegmentInfusionInfoEntity()
        assertThat(entity.createdAt).isEqualTo(createdDt.toString())
        assertThat(entity.updatedAt).isEqualTo(updatedDt.toString())
        assertThat(entity.startTime).isEqualTo(0)
        assertThat(entity.endTime).isEqualTo(30)
        assertThat(entity.speed).isEqualTo(0.5)
    }

    @Test
    fun `basal segment entity round trips through domain`() {
        val entity = CarelevoBasalSegmentInfusionInfoEntity(createdStr, updatedStr, 15, 45, 2.0)
        val back = entity.transformToCarelevoBasalSegmentInfusionInfoDomainModel()
            .transformToCarelevoBasalSegmentInfusionInfoEntity()
        assertThat(back).isEqualTo(entity)
    }

    // endregion

    // region Basal

    @Test
    fun `basal entity to domain maps scalar fields and segment list`() {
        val entity = CarelevoBasalInfusionInfoEntity(
            infusionId = "inf-1", address = "AA:BB", mode = 1,
            createdAt = createdStr, updatedAt = updatedStr,
            segments = listOf(
                CarelevoBasalSegmentInfusionInfoEntity(createdStr, updatedStr, 0, 60, 1.0),
                CarelevoBasalSegmentInfusionInfoEntity(createdStr, updatedStr, 60, 120, 2.0)
            ),
            isStop = false
        )
        val domain = entity.transformToCarelevoBasalInfusionInfoDomainModel()
        assertThat(domain.infusionId).isEqualTo("inf-1")
        assertThat(domain.address).isEqualTo("AA:BB")
        assertThat(domain.mode).isEqualTo(1)
        assertThat(domain.createdAt).isEqualTo(DateTime.parse(createdStr))
        assertThat(domain.updatedAt).isEqualTo(DateTime.parse(updatedStr))
        assertThat(domain.isStop).isFalse()
        assertThat(domain.segments).hasSize(2)
        assertThat(domain.segments[0].speed).isEqualTo(1.0)
        assertThat(domain.segments[1].speed).isEqualTo(2.0)
    }

    @Test
    fun `basal entity to domain with empty segment list`() {
        val entity = CarelevoBasalInfusionInfoEntity(
            "inf-2", "CC:DD", 0, createdStr, updatedStr, emptyList(), isStop = true
        )
        val domain = entity.transformToCarelevoBasalInfusionInfoDomainModel()
        assertThat(domain.segments).isEmpty()
        assertThat(domain.isStop).isTrue()
    }

    @Test
    fun `basal domain to entity maps scalar fields and segment list`() {
        val domain = CarelevoBasalInfusionInfoDomainModel(
            infusionId = "inf-3", address = "EE:FF", mode = 1,
            createdAt = createdDt, updatedAt = updatedDt,
            segments = listOf(CarelevoBasalSegmentInfusionInfoDomainModel(createdDt, updatedDt, 0, 90, 3.0)),
            isStop = false
        )
        val entity = domain.transformToCarelevoBasalInfusionInfoEntity()
        assertThat(entity.infusionId).isEqualTo("inf-3")
        assertThat(entity.address).isEqualTo("EE:FF")
        assertThat(entity.mode).isEqualTo(1)
        assertThat(entity.createdAt).isEqualTo(createdDt.toString())
        assertThat(entity.updatedAt).isEqualTo(updatedDt.toString())
        assertThat(entity.isStop).isFalse()
        assertThat(entity.segments).hasSize(1)
        assertThat(entity.segments[0].speed).isEqualTo(3.0)
    }

    @Test
    fun `basal entity round trips through domain`() {
        val entity = CarelevoBasalInfusionInfoEntity(
            "inf-4", "11:22", 1, createdStr, updatedStr,
            listOf(CarelevoBasalSegmentInfusionInfoEntity(createdStr, updatedStr, 0, 60, 1.5)),
            isStop = true
        )
        val back = entity.transformToCarelevoBasalInfusionInfoDomainModel()
            .transformToCarelevoBasalInfusionInfoEntity()
        assertThat(back).isEqualTo(entity)
    }

    // endregion

    // region TempBasal

    @Test
    fun `temp basal entity to domain with all optionals present`() {
        val entity = CarelevoTempBasalInfusionInfoEntity(
            infusionId = "t-1", address = "AA", mode = 2,
            createdAt = createdStr, updatedAt = updatedStr,
            percent = 150, speed = 2.5, infusionDurationMin = 30
        )
        val domain = entity.transformToCarelevoTempBasalInfusionInfoDomainModel()
        assertThat(domain.infusionId).isEqualTo("t-1")
        assertThat(domain.address).isEqualTo("AA")
        assertThat(domain.mode).isEqualTo(2)
        assertThat(domain.percent).isEqualTo(150)
        assertThat(domain.speed).isEqualTo(2.5)
        assertThat(domain.infusionDurationMin).isEqualTo(30)
    }

    @Test
    fun `temp basal entity to domain with all optionals null`() {
        val entity = CarelevoTempBasalInfusionInfoEntity(
            "t-2", "BB", 2, createdStr, updatedStr, percent = null, speed = null, infusionDurationMin = null
        )
        val domain = entity.transformToCarelevoTempBasalInfusionInfoDomainModel()
        assertThat(domain.percent).isNull()
        assertThat(domain.speed).isNull()
        assertThat(domain.infusionDurationMin).isNull()
    }

    @Test
    fun `temp basal domain to entity with all optionals present`() {
        val domain = CarelevoTempBasalInfusionInfoDomainModel(
            infusionId = "t-3", address = "CC", mode = 2,
            createdAt = createdDt, updatedAt = updatedDt,
            percent = 80, speed = 1.1, infusionDurationMin = 60
        )
        val entity = domain.transformToCarelevoTempBasalInfusionInfoEntity()
        assertThat(entity.infusionId).isEqualTo("t-3")
        assertThat(entity.createdAt).isEqualTo(createdDt.toString())
        assertThat(entity.updatedAt).isEqualTo(updatedDt.toString())
        assertThat(entity.percent).isEqualTo(80)
        assertThat(entity.speed).isEqualTo(1.1)
        assertThat(entity.infusionDurationMin).isEqualTo(60)
    }

    @Test
    fun `temp basal domain to entity with all optionals null`() {
        val domain = CarelevoTempBasalInfusionInfoDomainModel(
            "t-4", "DD", 2, createdDt, updatedDt, percent = null, speed = null, infusionDurationMin = null
        )
        val entity = domain.transformToCarelevoTempBasalInfusionInfoEntity()
        assertThat(entity.percent).isNull()
        assertThat(entity.speed).isNull()
        assertThat(entity.infusionDurationMin).isNull()
    }

    @Test
    fun `temp basal entity round trips through domain`() {
        val entity = CarelevoTempBasalInfusionInfoEntity("t-5", "EE", 2, createdStr, updatedStr, 200, 3.0, 45)
        val back = entity.transformToCarelevoTempBasalInfusionInfoDomainModel()
            .transformToCarelevoTempBasalInfusionInfoEntity()
        assertThat(back).isEqualTo(entity)
    }

    // endregion

    // region ImmeBolus

    @Test
    fun `imme bolus entity to domain with optionals present`() {
        val entity = CarelevoImmeBolusInfusionInfoEntity(
            infusionId = "i-1", address = "AA", mode = 3,
            createdAt = createdStr, updatedAt = updatedStr,
            volume = 4.5, infusionDurationSeconds = 90
        )
        val domain = entity.transformToCarelevoImmeBolusInfusionInfoDomainModel()
        assertThat(domain.infusionId).isEqualTo("i-1")
        assertThat(domain.mode).isEqualTo(3)
        assertThat(domain.volume).isEqualTo(4.5)
        assertThat(domain.infusionDurationSeconds).isEqualTo(90)
    }

    @Test
    fun `imme bolus entity to domain with optionals null`() {
        val entity = CarelevoImmeBolusInfusionInfoEntity("i-2", "BB", 3, createdStr, updatedStr, null, null)
        val domain = entity.transformToCarelevoImmeBolusInfusionInfoDomainModel()
        assertThat(domain.volume).isNull()
        assertThat(domain.infusionDurationSeconds).isNull()
    }

    @Test
    fun `imme bolus domain to entity with optionals present`() {
        val domain = CarelevoImmeBolusInfusionInfoDomainModel(
            "i-3", "CC", 3, createdDt, updatedDt, volume = 2.0, infusionDurationSeconds = 30
        )
        val entity = domain.transformToCarelevoImmeBolusInfusionInfoEntity()
        assertThat(entity.createdAt).isEqualTo(createdDt.toString())
        assertThat(entity.volume).isEqualTo(2.0)
        assertThat(entity.infusionDurationSeconds).isEqualTo(30)
    }

    @Test
    fun `imme bolus domain to entity with optionals null`() {
        val domain = CarelevoImmeBolusInfusionInfoDomainModel("i-4", "DD", 3, createdDt, updatedDt, null, null)
        val entity = domain.transformToCarelevoImmeBolusInfusionInfoEntity()
        assertThat(entity.volume).isNull()
        assertThat(entity.infusionDurationSeconds).isNull()
    }

    @Test
    fun `imme bolus entity round trips through domain`() {
        val entity = CarelevoImmeBolusInfusionInfoEntity("i-5", "EE", 3, createdStr, updatedStr, 5.0, 120)
        val back = entity.transformToCarelevoImmeBolusInfusionInfoDomainModel()
            .transformToCarelevoImmeBolusInfusionInfoEntity()
        assertThat(back).isEqualTo(entity)
    }

    // endregion

    // region ExtendBolus

    @Test
    fun `extend bolus entity to domain with optionals present`() {
        val entity = CarelevoExtendBolusInfusionInfoEntity(
            infusionId = "e-1", address = "AA", mode = 5,
            createdAt = createdStr, updatedAt = updatedStr,
            volume = 6.0, speed = 1.0, infusionDurationMin = 120
        )
        val domain = entity.transformToCarelevoExtendBolusInfusionInfoDomainModel()
        assertThat(domain.mode).isEqualTo(5)
        assertThat(domain.volume).isEqualTo(6.0)
        assertThat(domain.speed).isEqualTo(1.0)
        assertThat(domain.infusionDurationMin).isEqualTo(120)
    }

    @Test
    fun `extend bolus entity to domain with optionals null`() {
        val entity = CarelevoExtendBolusInfusionInfoEntity("e-2", "BB", 5, createdStr, updatedStr, null, null, null)
        val domain = entity.transformToCarelevoExtendBolusInfusionInfoDomainModel()
        assertThat(domain.volume).isNull()
        assertThat(domain.speed).isNull()
        assertThat(domain.infusionDurationMin).isNull()
    }

    @Test
    fun `extend bolus domain to entity with optionals present`() {
        val domain = CarelevoExtendBolusInfusionInfoDomainModel(
            "e-3", "CC", 5, createdDt, updatedDt, volume = 3.5, speed = 0.75, infusionDurationMin = 90
        )
        val entity = domain.transformToCarelevoExtendBolusInfusionInfoEntity()
        assertThat(entity.updatedAt).isEqualTo(updatedDt.toString())
        assertThat(entity.volume).isEqualTo(3.5)
        assertThat(entity.speed).isEqualTo(0.75)
        assertThat(entity.infusionDurationMin).isEqualTo(90)
    }

    @Test
    fun `extend bolus domain to entity with optionals null`() {
        val domain = CarelevoExtendBolusInfusionInfoDomainModel("e-4", "DD", 5, createdDt, updatedDt, null, null, null)
        val entity = domain.transformToCarelevoExtendBolusInfusionInfoEntity()
        assertThat(entity.volume).isNull()
        assertThat(entity.speed).isNull()
        assertThat(entity.infusionDurationMin).isNull()
    }

    @Test
    fun `extend bolus entity round trips through domain`() {
        val entity = CarelevoExtendBolusInfusionInfoEntity("e-5", "EE", 5, createdStr, updatedStr, 7.0, 1.25, 180)
        val back = entity.transformToCarelevoExtendBolusInfusionInfoDomainModel()
            .transformToCarelevoExtendBolusInfusionInfoEntity()
        assertThat(back).isEqualTo(entity)
    }

    // endregion

    // region Container (CarelevoInfusionInfo)

    @Test
    fun `infusion info entity to domain with all sub-infusions null`() {
        val domain = CarelevoInfusionInfoEntity().transformToCarelevoInfusionInfoDomainModel()
        assertThat(domain.basalInfusionInfo).isNull()
        assertThat(domain.tempBasalInfusionInfo).isNull()
        assertThat(domain.immeBolusInfusionInfo).isNull()
        assertThat(domain.extendBolusInfusionInfo).isNull()
    }

    @Test
    fun `infusion info entity to domain with all sub-infusions present`() {
        val entity = CarelevoInfusionInfoEntity(
            basalInfusionInfo = CarelevoBasalInfusionInfoEntity("b", "A", 1, createdStr, updatedStr, emptyList(), false),
            tempBasalInfusionInfo = CarelevoTempBasalInfusionInfoEntity("t", "A", 2, createdStr, updatedStr, 100, 1.0, 30),
            immeBolusInfusionInfo = CarelevoImmeBolusInfusionInfoEntity("i", "A", 3, createdStr, updatedStr, 2.0, 60),
            extendBolusInfusionInfo = CarelevoExtendBolusInfusionInfoEntity("e", "A", 5, createdStr, updatedStr, 3.0, 0.5, 90)
        )
        val domain = entity.transformToCarelevoInfusionInfoDomainModel()
        assertThat(domain.basalInfusionInfo?.infusionId).isEqualTo("b")
        assertThat(domain.tempBasalInfusionInfo?.infusionId).isEqualTo("t")
        assertThat(domain.immeBolusInfusionInfo?.infusionId).isEqualTo("i")
        assertThat(domain.extendBolusInfusionInfo?.infusionId).isEqualTo("e")
    }

    @Test
    fun `infusion info entity to domain with only temp basal present`() {
        val entity = CarelevoInfusionInfoEntity(
            tempBasalInfusionInfo = CarelevoTempBasalInfusionInfoEntity("t", "A", 2, createdStr, updatedStr)
        )
        val domain = entity.transformToCarelevoInfusionInfoDomainModel()
        assertThat(domain.basalInfusionInfo).isNull()
        assertThat(domain.tempBasalInfusionInfo).isNotNull()
        assertThat(domain.immeBolusInfusionInfo).isNull()
        assertThat(domain.extendBolusInfusionInfo).isNull()
    }

    @Test
    fun `infusion info domain to entity with all sub-infusions null`() {
        val entity = CarelevoInfusionInfoDomainModel().transformToCarelevoInfusionInfoEntity()
        assertThat(entity.basalInfusionInfo).isNull()
        assertThat(entity.tempBasalInfusionInfo).isNull()
        assertThat(entity.immeBolusInfusionInfo).isNull()
        assertThat(entity.extendBolusInfusionInfo).isNull()
    }

    @Test
    fun `infusion info domain to entity with all sub-infusions present`() {
        val domain = CarelevoInfusionInfoDomainModel(
            basalInfusionInfo = CarelevoBasalInfusionInfoDomainModel("b", "A", 1, createdDt, updatedDt, emptyList(), false),
            tempBasalInfusionInfo = CarelevoTempBasalInfusionInfoDomainModel("t", "A", 2, createdDt, updatedDt, 100, 1.0, 30),
            immeBolusInfusionInfo = CarelevoImmeBolusInfusionInfoDomainModel("i", "A", 3, createdDt, updatedDt, 2.0, 60),
            extendBolusInfusionInfo = CarelevoExtendBolusInfusionInfoDomainModel("e", "A", 5, createdDt, updatedDt, 3.0, 0.5, 90)
        )
        val entity = domain.transformToCarelevoInfusionInfoEntity()
        assertThat(entity.basalInfusionInfo?.infusionId).isEqualTo("b")
        assertThat(entity.tempBasalInfusionInfo?.infusionId).isEqualTo("t")
        assertThat(entity.immeBolusInfusionInfo?.infusionId).isEqualTo("i")
        assertThat(entity.extendBolusInfusionInfo?.infusionId).isEqualTo("e")
    }

    @Test
    fun `infusion info domain to entity with only extend bolus present`() {
        val domain = CarelevoInfusionInfoDomainModel(
            extendBolusInfusionInfo = CarelevoExtendBolusInfusionInfoDomainModel("e", "A", 5, createdDt, updatedDt)
        )
        val entity = domain.transformToCarelevoInfusionInfoEntity()
        assertThat(entity.basalInfusionInfo).isNull()
        assertThat(entity.tempBasalInfusionInfo).isNull()
        assertThat(entity.immeBolusInfusionInfo).isNull()
        assertThat(entity.extendBolusInfusionInfo).isNotNull()
    }

    @Test
    fun `infusion info entity round trips through domain when fully populated`() {
        val entity = CarelevoInfusionInfoEntity(
            basalInfusionInfo = CarelevoBasalInfusionInfoEntity(
                "b", "A", 1, createdStr, updatedStr,
                listOf(CarelevoBasalSegmentInfusionInfoEntity(createdStr, updatedStr, 0, 60, 1.0)), false
            ),
            tempBasalInfusionInfo = CarelevoTempBasalInfusionInfoEntity("t", "A", 2, createdStr, updatedStr, 100, 1.0, 30),
            immeBolusInfusionInfo = CarelevoImmeBolusInfusionInfoEntity("i", "A", 3, createdStr, updatedStr, 2.0, 60),
            extendBolusInfusionInfo = CarelevoExtendBolusInfusionInfoEntity("e", "A", 5, createdStr, updatedStr, 3.0, 0.5, 90)
        )
        val back = entity.transformToCarelevoInfusionInfoDomainModel().transformToCarelevoInfusionInfoEntity()
        assertThat(back).isEqualTo(entity)
    }

    @Test
    fun `empty infusion info entity round trips to empty`() {
        val entity = CarelevoInfusionInfoEntity()
        val back = entity.transformToCarelevoInfusionInfoDomainModel().transformToCarelevoInfusionInfoEntity()
        assertThat(back).isEqualTo(entity)
    }

    // endregion
}
