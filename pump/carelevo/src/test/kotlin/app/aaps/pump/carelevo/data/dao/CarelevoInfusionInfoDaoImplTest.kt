package app.aaps.pump.carelevo.data.dao

import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalSegmentInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional

/**
 * Unit tests for [CarelevoInfusionInfoDaoImpl] — exercises the preference-backed round-trip
 * (save / load / update-field / delete) using an in-memory [MutableMap] behind the mocked
 * [Preferences], plus the aggregate [CarelevoInfusionInfoEntity] BehaviorSubject folding,
 * empty/absent-record collapse, malformed-JSON tolerance and persistence-failure branches.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoInfusionInfoDaoImplTest {

    @Mock lateinit var preferences: Preferences

    private lateinit var store: MutableMap<String, String>
    private lateinit var sut: CarelevoInfusionInfoDaoImpl

    private val gson get() = CarelevoGsonHelper.sharedGson()

    // ---- entity builders -------------------------------------------------------------------

    private fun basal(id: String = "b1") = CarelevoBasalInfusionInfoEntity(
        infusionId = id,
        address = "AA:BB:CC:DD:EE:FF",
        mode = 1,
        createdAt = "2026-07-16T10:00:00",
        updatedAt = "2026-07-16T10:00:00",
        segments = listOf(
            CarelevoBasalSegmentInfusionInfoEntity(
                createdAt = "2026-07-16T10:00:00",
                updatedAt = "2026-07-16T10:00:00",
                startTime = 0,
                endTime = 60,
                speed = 1.0
            )
        ),
        isStop = false
    )

    private fun tempBasal(id: String = "tb1") = CarelevoTempBasalInfusionInfoEntity(
        infusionId = id,
        address = "AA:BB:CC:DD:EE:FF",
        mode = 2,
        createdAt = "2026-07-16T10:00:00",
        updatedAt = "2026-07-16T10:00:00",
        percent = 150,
        speed = 1.5,
        infusionDurationMin = 30
    )

    private fun immeBolus(id: String = "ib1") = CarelevoImmeBolusInfusionInfoEntity(
        infusionId = id,
        address = "AA:BB:CC:DD:EE:FF",
        mode = 3,
        createdAt = "2026-07-16T10:00:00",
        updatedAt = "2026-07-16T10:00:00",
        volume = 2.0,
        infusionDurationSeconds = 120
    )

    private fun extendBolus(id: String = "eb1") = CarelevoExtendBolusInfusionInfoEntity(
        infusionId = id,
        address = "AA:BB:CC:DD:EE:FF",
        mode = 4,
        createdAt = "2026-07-16T10:00:00",
        updatedAt = "2026-07-16T10:00:00",
        volume = 3.0,
        speed = 0.5,
        infusionDurationMin = 45
    )

    private fun seed(key: StringNonPreferenceKey, value: Any) {
        store[key.key] = gson.toJson(value)
    }

    @BeforeEach
    fun setUp() {
        store = mutableMapOf()
        // get(key) — non-void, backed by the in-memory store; falls back to the key's own default.
        whenever(preferences.get(any<StringNonPreferenceKey>())).thenAnswer { inv ->
            val key = inv.getArgument<StringNonPreferenceKey>(0)
            store[key.key] ?: key.defaultValue
        }
        // put(key, value) — void; write through to the store.
        doAnswer { inv ->
            store[inv.getArgument<StringNonPreferenceKey>(0).key] = inv.getArgument<String>(1)
            null
        }.whenever(preferences).put(any<StringNonPreferenceKey>(), any<String>())
        // remove(key) — void; delete from the store.
        doAnswer { inv ->
            store.remove(inv.getArgument<NonPreferenceKey>(0).key)
            null
        }.whenever(preferences).remove(any<NonPreferenceKey>())

        sut = CarelevoInfusionInfoDaoImpl(preferences)
    }

    // ---- getInfusionInfo / ensureLoaded ----------------------------------------------------

    @Test
    fun `getInfusionInfo on empty store emits an absent optional`() {
        val result = sut.getInfusionInfo().blockingFirst()
        assertThat(result.isPresent).isFalse()
    }

    @Test
    fun `getInfusionInfo seeds the aggregate from a stored basal record`() {
        seed(CarelevoStringNonKey.BasalInfusionInfo, basal())

        val result = sut.getInfusionInfo().blockingFirst()

        assertThat(result.isPresent).isTrue()
        assertThat(result.get().basalInfusionInfo).isEqualTo(basal())
        assertThat(result.get().tempBasalInfusionInfo).isNull()
        assertThat(result.get().immeBolusInfusionInfo).isNull()
        assertThat(result.get().extendBolusInfusionInfo).isNull()
    }

    @Test
    fun `getInfusionInfoBySync on empty store returns null`() {
        assertThat(sut.getInfusionInfoBySync()).isNull()
    }

    @Test
    fun `getInfusionInfoBySync seeds all four records from prefs`() {
        seed(CarelevoStringNonKey.BasalInfusionInfo, basal())
        seed(CarelevoStringNonKey.TempBasalInfusionInfo, tempBasal())
        seed(CarelevoStringNonKey.ImmeBolusInfusionInfo, immeBolus())
        seed(CarelevoStringNonKey.ExtendBolusInfusionInfo, extendBolus())

        val agg = sut.getInfusionInfoBySync()

        assertThat(agg).isNotNull()
        assertThat(agg!!.basalInfusionInfo).isEqualTo(basal())
        assertThat(agg.tempBasalInfusionInfo).isEqualTo(tempBasal())
        assertThat(agg.immeBolusInfusionInfo).isEqualTo(immeBolus())
        assertThat(agg.extendBolusInfusionInfo).isEqualTo(extendBolus())
    }

    @Test
    fun `ensureLoaded reads prefs only once then serves the cached subject value`() {
        // First read seeds from an empty store (absent).
        assertThat(sut.getInfusionInfoBySync()).isNull()
        // Even though data appears in prefs afterwards, the cached subject is not reloaded.
        seed(CarelevoStringNonKey.BasalInfusionInfo, basal())
        assertThat(sut.getInfusionInfoBySync()).isNull()

        // Exactly one load pass = four get calls (one per per-mode key).
        verify(preferences, times(4)).get(any<StringNonPreferenceKey>())
    }

    @Test
    fun `loadEntity tolerates malformed json by treating that record as absent`() {
        store[CarelevoStringNonKey.BasalInfusionInfo.key] = "[1,2,3]" // not a JSON object → parse fails
        seed(CarelevoStringNonKey.TempBasalInfusionInfo, tempBasal())

        val agg = sut.getInfusionInfoBySync()

        assertThat(agg).isNotNull()
        assertThat(agg!!.basalInfusionInfo).isNull()
        assertThat(agg.tempBasalInfusionInfo).isEqualTo(tempBasal())
    }

    // ---- updateXxxInfusionInfo (success + fold) --------------------------------------------

    @Test
    fun `updateBasalInfusionInfo persists json and folds into the aggregate`() {
        val b = basal()

        assertThat(sut.updateBasalInfusionInfo(b)).isTrue()

        assertThat(store[CarelevoStringNonKey.BasalInfusionInfo.key]).isEqualTo(gson.toJson(b))
        assertThat(sut.getInfusionInfoBySync()!!.basalInfusionInfo).isEqualTo(b)
        verify(preferences).put(eq<StringNonPreferenceKey>(CarelevoStringNonKey.BasalInfusionInfo), any<String>())
    }

    @Test
    fun `updateTempBasalInfusionInfo persists json and folds into the aggregate`() {
        val tb = tempBasal()

        assertThat(sut.updateTempBasalInfusionInfo(tb)).isTrue()

        assertThat(store[CarelevoStringNonKey.TempBasalInfusionInfo.key]).isEqualTo(gson.toJson(tb))
        assertThat(sut.getInfusionInfoBySync()!!.tempBasalInfusionInfo).isEqualTo(tb)
        verify(preferences).put(eq<StringNonPreferenceKey>(CarelevoStringNonKey.TempBasalInfusionInfo), any<String>())
    }

    @Test
    fun `updateImmeBolusInfusionInfo persists json and folds into the aggregate`() {
        val ib = immeBolus()

        assertThat(sut.updateImmeBolusInfusionInfo(ib)).isTrue()

        assertThat(store[CarelevoStringNonKey.ImmeBolusInfusionInfo.key]).isEqualTo(gson.toJson(ib))
        assertThat(sut.getInfusionInfoBySync()!!.immeBolusInfusionInfo).isEqualTo(ib)
        verify(preferences).put(eq<StringNonPreferenceKey>(CarelevoStringNonKey.ImmeBolusInfusionInfo), any<String>())
    }

    @Test
    fun `updateExtendBolusInfusionInfo persists json and folds into the aggregate`() {
        val eb = extendBolus()

        assertThat(sut.updateExtendBolusInfusionInfo(eb)).isTrue()

        assertThat(store[CarelevoStringNonKey.ExtendBolusInfusionInfo.key]).isEqualTo(gson.toJson(eb))
        assertThat(sut.getInfusionInfoBySync()!!.extendBolusInfusionInfo).isEqualTo(eb)
        verify(preferences).put(eq<StringNonPreferenceKey>(CarelevoStringNonKey.ExtendBolusInfusionInfo), any<String>())
    }

    @Test
    fun `sequential updates accumulate onto the existing aggregate`() {
        // First update takes the value==null branch; the rest fold onto the present aggregate.
        assertThat(sut.updateBasalInfusionInfo(basal())).isTrue()
        assertThat(sut.updateTempBasalInfusionInfo(tempBasal())).isTrue()
        assertThat(sut.updateImmeBolusInfusionInfo(immeBolus())).isTrue()
        assertThat(sut.updateExtendBolusInfusionInfo(extendBolus())).isTrue()

        val agg = sut.getInfusionInfoBySync()!!
        assertThat(agg.basalInfusionInfo).isEqualTo(basal())
        assertThat(agg.tempBasalInfusionInfo).isEqualTo(tempBasal())
        assertThat(agg.immeBolusInfusionInfo).isEqualTo(immeBolus())
        assertThat(agg.extendBolusInfusionInfo).isEqualTo(extendBolus())
    }

    @Test
    fun `updateBasalInfusionInfo returns false and leaves the aggregate untouched when persistence throws`() {
        doThrow(RuntimeException("write failed")).whenever(preferences).put(any<StringNonPreferenceKey>(), any<String>())

        assertThat(sut.updateBasalInfusionInfo(basal())).isFalse()
        // Nothing was persisted, so a fresh load still reports absent.
        assertThat(sut.getInfusionInfoBySync()).isNull()
    }

    @Test
    fun `updating emits the folded value on the observable`() {
        val observer = sut.getInfusionInfo().test()

        assertThat(sut.updateBasalInfusionInfo(basal())).isTrue()

        val last: Optional<CarelevoInfusionInfoEntity> = observer.values().last()
        assertThat(last.orElse(null)?.basalInfusionInfo).isEqualTo(basal())
        observer.dispose()
    }

    // ---- deleteXxxInfusionInfo -------------------------------------------------------------

    @Test
    fun `deleteBasalInfusionInfo removes the key and collapses the aggregate to absent`() {
        assertThat(sut.updateBasalInfusionInfo(basal())).isTrue()

        assertThat(sut.deleteBasalInfusionInfo()).isTrue()

        assertThat(store.containsKey(CarelevoStringNonKey.BasalInfusionInfo.key)).isFalse()
        assertThat(sut.getInfusionInfoBySync()).isNull()
        verify(preferences).remove(eq<NonPreferenceKey>(CarelevoStringNonKey.BasalInfusionInfo))
    }

    @Test
    fun `deleteTempBasalInfusionInfo removes the key and collapses the aggregate to absent`() {
        assertThat(sut.updateTempBasalInfusionInfo(tempBasal())).isTrue()

        assertThat(sut.deleteTempBasalInfusionInfo()).isTrue()

        assertThat(store.containsKey(CarelevoStringNonKey.TempBasalInfusionInfo.key)).isFalse()
        assertThat(sut.getInfusionInfoBySync()).isNull()
        verify(preferences).remove(eq<NonPreferenceKey>(CarelevoStringNonKey.TempBasalInfusionInfo))
    }

    @Test
    fun `deleteImmeBolusInfusionInfo removes the key and collapses the aggregate to absent`() {
        assertThat(sut.updateImmeBolusInfusionInfo(immeBolus())).isTrue()

        assertThat(sut.deleteImmeBolusInfusionInfo()).isTrue()

        assertThat(store.containsKey(CarelevoStringNonKey.ImmeBolusInfusionInfo.key)).isFalse()
        assertThat(sut.getInfusionInfoBySync()).isNull()
        verify(preferences).remove(eq<NonPreferenceKey>(CarelevoStringNonKey.ImmeBolusInfusionInfo))
    }

    @Test
    fun `deleteExtendBolusInfusionInfo removes the key and collapses the aggregate to absent`() {
        assertThat(sut.updateExtendBolusInfusionInfo(extendBolus())).isTrue()

        assertThat(sut.deleteExtendBolusInfusionInfo()).isTrue()

        assertThat(store.containsKey(CarelevoStringNonKey.ExtendBolusInfusionInfo.key)).isFalse()
        assertThat(sut.getInfusionInfoBySync()).isNull()
        verify(preferences).remove(eq<NonPreferenceKey>(CarelevoStringNonKey.ExtendBolusInfusionInfo))
    }

    @Test
    fun `deleteBasalInfusionInfo keeps the aggregate when other records remain`() {
        assertThat(sut.updateBasalInfusionInfo(basal())).isTrue()
        assertThat(sut.updateTempBasalInfusionInfo(tempBasal())).isTrue()

        assertThat(sut.deleteBasalInfusionInfo()).isTrue()

        val agg = sut.getInfusionInfoBySync()!!
        assertThat(agg.basalInfusionInfo).isNull()
        assertThat(agg.tempBasalInfusionInfo).isEqualTo(tempBasal())
    }

    @Test
    fun `deleteBasalInfusionInfo on an absent aggregate stays absent and still succeeds`() {
        // No prior state: the subject has never been populated (value == null branch).
        assertThat(sut.deleteBasalInfusionInfo()).isTrue()

        assertThat(sut.getInfusionInfoBySync()).isNull()
        verify(preferences).remove(eq<NonPreferenceKey>(CarelevoStringNonKey.BasalInfusionInfo))
    }

    @Test
    fun `deleteBasalInfusionInfo returns false when the remove throws`() {
        doThrow(RuntimeException("remove failed")).whenever(preferences).remove(any<NonPreferenceKey>())

        assertThat(sut.deleteBasalInfusionInfo()).isFalse()
    }

    // ---- deleteInfusionInfo (all) ----------------------------------------------------------

    @Test
    fun `deleteInfusionInfo removes all four keys and emits an absent aggregate`() {
        assertThat(sut.updateBasalInfusionInfo(basal())).isTrue()
        assertThat(sut.updateTempBasalInfusionInfo(tempBasal())).isTrue()
        assertThat(sut.updateImmeBolusInfusionInfo(immeBolus())).isTrue()
        assertThat(sut.updateExtendBolusInfusionInfo(extendBolus())).isTrue()

        assertThat(sut.deleteInfusionInfo()).isTrue()

        assertThat(store).isEmpty()
        assertThat(sut.getInfusionInfoBySync()).isNull()
        verify(preferences).remove(eq<NonPreferenceKey>(CarelevoStringNonKey.BasalInfusionInfo))
        verify(preferences).remove(eq<NonPreferenceKey>(CarelevoStringNonKey.TempBasalInfusionInfo))
        verify(preferences).remove(eq<NonPreferenceKey>(CarelevoStringNonKey.ImmeBolusInfusionInfo))
        verify(preferences).remove(eq<NonPreferenceKey>(CarelevoStringNonKey.ExtendBolusInfusionInfo))
    }

    @Test
    fun `deleteInfusionInfo returns false when a remove throws`() {
        doThrow(RuntimeException("remove failed")).whenever(preferences).remove(any<NonPreferenceKey>())

        assertThat(sut.deleteInfusionInfo()).isFalse()
    }

    @Test
    fun `deleteInfusionInfo emits an absent optional on the observable`() {
        assertThat(sut.updateBasalInfusionInfo(basal())).isTrue()
        val observer = sut.getInfusionInfo().test()

        assertThat(sut.deleteInfusionInfo()).isTrue()

        assertThat(observer.values().last().isPresent).isFalse()
        observer.dispose()
    }
}
