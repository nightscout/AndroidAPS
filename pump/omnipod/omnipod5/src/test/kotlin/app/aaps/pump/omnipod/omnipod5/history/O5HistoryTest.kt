package app.aaps.pump.omnipod.omnipod5.history

import app.aaps.core.interfaces.profile.Profile
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BolusType
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.common.history.data.BolusRecord
import app.aaps.pump.omnipod.common.history.data.HistoryRecord
import app.aaps.pump.omnipod.common.history.data.InitialResult
import app.aaps.pump.omnipod.common.history.data.ResolvedResult
import app.aaps.pump.omnipod.common.history.data.TempBasalRecord
import app.aaps.pump.omnipod.omnipod5.history.database.HistoryRecordDao
import app.aaps.pump.omnipod.omnipod5.history.database.HistoryRecordEntity
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class O5HistoryTest {

    private val dao = mock<HistoryRecordDao>()
    private val mapper = HistoryMapper()
    private val history = O5History(dao, mapper)

    private fun entity(
        commandType: OmnipodCommandType = OmnipodCommandType.SET_BOLUS,
        bolus: BolusRecord? = null,
        tempBasal: TempBasalRecord? = null,
        basal: BasalValuesRecord? = null
    ) = HistoryRecordEntity(
        id = 42L,
        createdAt = 1_000L,
        date = 900L,
        commandType = commandType,
        initialResult = InitialResult.SENT,
        tempBasalRecord = tempBasal,
        bolusRecord = bolus,
        basalProfileRecord = basal,
        totalAmountDelivered = 0.15,
        resolvedResult = ResolvedResult.SUCCESS,
        resolvedAt = 1_100L
    )

    @Test
    fun `mapper preserves shared bolus records and history fields`() {
        BolusType.entries.forEach { type ->
            val bolus = BolusRecord(0.2, type)
            val record = mapper.toDomain(entity(bolus = bolus))

            assertThat(record).isEqualTo(
                HistoryRecord(
                    id = 42L,
                    createdAt = 1_000L,
                    date = 900L,
                    commandType = OmnipodCommandType.SET_BOLUS,
                    initialResult = InitialResult.SENT,
                    record = bolus,
                    totalAmountDelivered = 0.15,
                    resolvedResult = ResolvedResult.SUCCESS,
                    resolvedAt = 1_100L
                )
            )
            assertThat(record.pumpId()).isEqualTo(42L)
            assertThat(record.displayTimestamp()).isEqualTo(900L)
            assertThat(record.isSuccess()).isTrue()
        }
    }

    @Test
    fun `mapper preserves shared temp basal and basal profile records`() {
        val tempBasal = TempBasalRecord(30, 0.5)
        val basal = BasalValuesRecord(listOf(Profile.ProfileValue(0, 0.8)))

        assertThat(mapper.toDomain(entity(tempBasal = tempBasal)).record).isEqualTo(tempBasal)
        assertThat(mapper.toDomain(entity(basal = basal)).record).isEqualTo(basal)
        assertThat(mapper.toDomain(entity()).record).isNull()
    }

    @Test
    fun `unresolved and failed records are not successful`() {
        val source = entity()
        assertThat(mapper.toDomain(source.copy(resolvedResult = null, resolvedAt = null)).isSuccess()).isFalse()
        assertThat(mapper.toDomain(source.copy(resolvedResult = ResolvedResult.FAILURE)).isSuccess()).isFalse()
        assertThat(mapper.toDomain(source.copy(initialResult = InitialResult.FAILURE_SENDING)).isSuccess()).isFalse()
    }

    @Test
    fun `history creates a record with the shared bolus type`() {
        val bolus = BolusRecord(0.2, BolusType.BASAL_CORRECTION)
        whenever(dao.save(any())).thenReturn(Single.just(42L))

        val id = history.createRecord(
            commandType = OmnipodCommandType.SET_BOLUS,
            date = 900L,
            bolusRecord = bolus
        ).blockingGet()

        val saved = argumentCaptor<HistoryRecordEntity>()
        verify(dao).save(saved.capture())
        assertThat(id).isEqualTo(42L)
        assertThat(saved.firstValue.bolusRecord).isEqualTo(bolus)
        assertThat(saved.firstValue.date).isEqualTo(900L)
        assertThat(saved.firstValue.initialResult).isEqualTo(InitialResult.SENT)
        assertThat(saved.firstValue.resolvedResult).isNull()
    }

    @Test
    fun `history reads shared records by id and in lists`() {
        val source = entity(bolus = BolusRecord(0.2, BolusType.SMB))
        val expected = mapper.toDomain(source)
        whenever(dao.byIdBlocking(42L)).thenReturn(source)
        whenever(dao.all()).thenReturn(Single.just(listOf(source)))
        whenever(dao.allSince(500L)).thenReturn(Single.just(listOf(source)))

        assertThat(history.getById(42L)).isEqualTo(expected)
        assertThat(history.getRecords().blockingGet()).containsExactly(expected)
        assertThat(history.getRecordsAfter(500L).blockingGet()).containsExactly(expected)
    }

    @Test
    fun `history deletes each shared record without changing the entity`() {
        val sources = listOf(
            entity(bolus = BolusRecord(0.2, BolusType.BASAL_CORRECTION)),
            entity(OmnipodCommandType.SET_TEMPORARY_BASAL, tempBasal = TempBasalRecord(30, 0.5)),
            entity(OmnipodCommandType.SET_BASAL_PROFILE, basal = BasalValuesRecord(listOf(Profile.ProfileValue(0, 0.8))))
        )
        whenever(dao.delete(any())).thenReturn(Completable.complete())

        sources.forEach { source ->
            history.delete(mapper.toDomain(source)).blockingAwait()
            verify(dao).delete(source)
        }
    }
}
