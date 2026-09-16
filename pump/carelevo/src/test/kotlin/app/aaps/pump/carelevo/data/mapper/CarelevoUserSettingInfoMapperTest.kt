package app.aaps.pump.carelevo.data.mapper

import app.aaps.pump.carelevo.ext.parseIsoInstant
import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import com.google.common.truth.Truth.assertThat
import kotlin.time.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class CarelevoUserSettingInfoMapperTest {

    // Canonical ISO-8601 strings (Joda Instant.toString() form) so the entity round-trip is exact.
    private val createdAtStr = "2024-03-10T12:00:00.000+02:00"
    private val updatedAtStr = "2024-03-11T18:45:15.250+02:00"

    private fun fullEntity() = CarelevoUserSettingInfoEntity(
        createdAt = createdAtStr,
        updatedAt = updatedAtStr,
        lowInsulinNoticeAmount = 30,
        maxBasalSpeed = 2.5,
        maxBolusDose = 15.0,
        needLowInsulinNoticeAmountSyncPatch = true,
        needMaxBasalSpeedSyncPatch = false,
        needMaxBolusDoseSyncPatch = true
    )

    private fun fullDomain() = CarelevoUserSettingInfoDomainModel(
        createdAt = parseIsoInstant(createdAtStr),
        updatedAt = parseIsoInstant(updatedAtStr),
        lowInsulinNoticeAmount = 30,
        maxBasalSpeed = 2.5,
        maxBolusDose = 15.0,
        needLowInsulinNoticeAmountSyncPatch = true,
        needMaxBasalSpeedSyncPatch = false,
        needMaxBolusDoseSyncPatch = true
    )

    // ---------- Entity -> Domain ----------

    @Test
    fun `entity to domain maps every scalar field`() {
        val d = fullEntity().transformToCarelevoUserSettingInfoDomainModel()
        assertThat(d.lowInsulinNoticeAmount).isEqualTo(30)
        assertThat(d.maxBasalSpeed).isEqualTo(2.5)
        assertThat(d.maxBolusDose).isEqualTo(15.0)
        assertThat(d.needLowInsulinNoticeAmountSyncPatch).isTrue()
        assertThat(d.needMaxBasalSpeedSyncPatch).isFalse()
        assertThat(d.needMaxBolusDoseSyncPatch).isTrue()
    }

    @Test
    fun `entity to domain parses createdAt and updatedAt strings`() {
        val d = fullEntity().transformToCarelevoUserSettingInfoDomainModel()
        assertThat(d.createdAt).isEqualTo(parseIsoInstant(createdAtStr))
        assertThat(d.updatedAt).isEqualTo(parseIsoInstant(updatedAtStr))
        assertThat(d.updatedAt).isEqualTo(parseIsoInstant(updatedAtStr))
    }

    @Test
    fun `entity to domain preserves null optionals and default false flags`() {
        val minimal = CarelevoUserSettingInfoEntity(createdAt = createdAtStr, updatedAt = updatedAtStr)
        val d = minimal.transformToCarelevoUserSettingInfoDomainModel()
        assertThat(d.lowInsulinNoticeAmount).isNull()
        assertThat(d.maxBasalSpeed).isNull()
        assertThat(d.maxBolusDose).isNull()
        assertThat(d.needLowInsulinNoticeAmountSyncPatch).isFalse()
        assertThat(d.needMaxBasalSpeedSyncPatch).isFalse()
        assertThat(d.needMaxBolusDoseSyncPatch).isFalse()
    }

    @Test
    fun `entity to domain throws on unparseable createdAt`() {
        val bad = CarelevoUserSettingInfoEntity(createdAt = "nope", updatedAt = updatedAtStr)
        assertFailsWith<IllegalArgumentException> { bad.transformToCarelevoUserSettingInfoDomainModel() }
    }

    @Test
    fun `entity to domain throws on unparseable updatedAt`() {
        val bad = CarelevoUserSettingInfoEntity(createdAt = createdAtStr, updatedAt = "")
        assertFailsWith<IllegalArgumentException> { bad.transformToCarelevoUserSettingInfoDomainModel() }
    }

    // ---------- Domain -> Entity ----------

    @Test
    fun `domain to entity maps every scalar field`() {
        val e = fullDomain().transformToCarelevoUserSettingInfoEntity()
        assertThat(e.lowInsulinNoticeAmount).isEqualTo(30)
        assertThat(e.maxBasalSpeed).isEqualTo(2.5)
        assertThat(e.maxBolusDose).isEqualTo(15.0)
        assertThat(e.needLowInsulinNoticeAmountSyncPatch).isTrue()
        assertThat(e.needMaxBasalSpeedSyncPatch).isFalse()
        assertThat(e.needMaxBolusDoseSyncPatch).isTrue()
    }

    @Test
    fun `domain to entity serializes dates via toString`() {
        val domain = fullDomain()
        val e = domain.transformToCarelevoUserSettingInfoEntity()
        assertThat(e.createdAt).isEqualTo(domain.createdAt.toString())
        assertThat(e.updatedAt).isEqualTo(domain.updatedAt.toString())
        // Instant writes UTC, so a value stored with a local offset comes back normalised - the text
        // changes, the instant does not. Compare the instant, which is what the round trip must keep.
        assertThat(parseIsoInstant(e.createdAt)).isEqualTo(parseIsoInstant(createdAtStr))
        assertThat(parseIsoInstant(e.updatedAt)).isEqualTo(parseIsoInstant(updatedAtStr))
    }

    @Test
    fun `domain to entity preserves null optionals and default false flags`() {
        val minimal = CarelevoUserSettingInfoDomainModel(
            createdAt = parseIsoInstant(createdAtStr),
            updatedAt = parseIsoInstant(updatedAtStr)
        )
        val e = minimal.transformToCarelevoUserSettingInfoEntity()
        assertThat(e.lowInsulinNoticeAmount).isNull()
        assertThat(e.maxBasalSpeed).isNull()
        assertThat(e.maxBolusDose).isNull()
        assertThat(e.needLowInsulinNoticeAmountSyncPatch).isFalse()
        assertThat(e.needMaxBasalSpeedSyncPatch).isFalse()
        assertThat(e.needMaxBolusDoseSyncPatch).isFalse()
    }

    // ---------- Round trips ----------

    @Test
    fun `round trip domain to entity to domain preserves values`() {
        val original = fullDomain()
        val result = original.transformToCarelevoUserSettingInfoEntity().transformToCarelevoUserSettingInfoDomainModel()
        assertThat(result.createdAt).isEqualTo(original.createdAt)
        assertThat(result.updatedAt).isEqualTo(original.updatedAt)
        assertThat(result.copy(createdAt = original.createdAt, updatedAt = original.updatedAt)).isEqualTo(original)
    }

    @Test
    fun `round trip entity to domain to entity preserves instants and values`() {
        val original = fullEntity()
        val result = original.transformToCarelevoUserSettingInfoDomainModel().transformToCarelevoUserSettingInfoEntity()
        // Every non-date field survives the cycle byte for byte.
        assertThat(result.copy(createdAt = original.createdAt, updatedAt = original.updatedAt)).isEqualTo(original)
        // The dates survive as instants. The text is rewritten to UTC because that is what
        // Instant.toString does, so an entity stored with a local offset does not come back verbatim.
        assertThat(parseIsoInstant(result.createdAt)).isEqualTo(parseIsoInstant(original.createdAt))
        assertThat(parseIsoInstant(result.updatedAt)).isEqualTo(parseIsoInstant(original.updatedAt))
    }

    @Test
    fun `sync flags map with inverted pattern`() {
        val e = fullEntity().copy(
            needLowInsulinNoticeAmountSyncPatch = false,
            needMaxBasalSpeedSyncPatch = true,
            needMaxBolusDoseSyncPatch = false
        )
        val d = e.transformToCarelevoUserSettingInfoDomainModel()
        assertThat(d.needLowInsulinNoticeAmountSyncPatch).isFalse()
        assertThat(d.needMaxBasalSpeedSyncPatch).isTrue()
        assertThat(d.needMaxBolusDoseSyncPatch).isFalse()
    }

    @Test
    fun `zero and negative numeric values pass through unchanged`() {
        val e = fullEntity().copy(lowInsulinNoticeAmount = 0, maxBasalSpeed = 0.0, maxBolusDose = -1.5)
        val d = e.transformToCarelevoUserSettingInfoDomainModel()
        assertThat(d.lowInsulinNoticeAmount).isEqualTo(0)
        assertThat(d.maxBasalSpeed).isEqualTo(0.0)
        assertThat(d.maxBolusDose).isEqualTo(-1.5)
    }
}
