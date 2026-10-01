package app.aaps.pump.common.sync

import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * [PumpSyncStorage] keeps the pump records that were written with a temporary id, so a driver can pair
 * them up once the pump reports the real one. Losing an entry here means a delivered bolus that never
 * reaches the database, so the round trip through preferences and the add/remove bookkeeping are worth
 * pinning.
 */
internal class PumpSyncStorageTest {

    private val pumpSync: PumpSync = mock()
    private val aapsLogger: AAPSLogger = mock()

    /** Preferences backed by a map, so a save can actually be read back by the next initStorage(). */
    private val stored = mutableMapOf<String, String>()
    private val preferences: Preferences = mock()

    private lateinit var sut: PumpSyncStorage

    private val creator = object : PumpSyncEntriesCreator {
        var nextTempId = 111L
        override fun generateTempId(objectA: Any): Long = nextTempId
        override fun model(): PumpType = PumpType.OMNIPOD_DASH
        override fun serialNumber(): String = "SERIAL-1"
    }

    @BeforeEach fun setUp() {
        stored.clear()
        // Reads come straight back out of the map, the way a real store behaves. It matters now that
        // there is no in-memory list: a write followed by a read has to go through the store, so a
        // mock that always answers null would make every add look like it did nothing.
        whenever(preferences.getIfExists(any<StringNonKey>())).thenAnswer { inv ->
            stored[(inv.arguments[0] as StringNonKey).key]
        }
        whenever(preferences.put(any<StringNonKey>(), any<String>())).thenAnswer { inv ->
            stored[(inv.arguments[0] as StringNonKey).key] = inv.arguments[1] as String
            Unit
        }
        whenever(preferences.remove(any<StringNonKey>())).thenAnswer { inv ->
            stored.remove((inv.arguments[0] as StringNonKey).key)
            Unit
        }
        sut = PumpSyncStorage(pumpSync, preferences, aapsLogger)
    }

    /** Makes later reads see whatever the previous save wrote. */
    private fun replayStoredPreferences() {
        whenever(preferences.getIfExists(any<StringNonKey>())).thenAnswer { inv ->
            stored[(inv.arguments[0] as StringNonKey).key]
        }
    }

    /**
     * Puts entries in the store, which is the only copy there is.
     *
     * The tests used to seed with `sut.getBoluses().add(...)`, which worked only because the getter
     * handed back the live list. That aliasing is exactly what was removed - a caller mutating the
     * returned list never reached the store - so seeding now goes where the data really lives.
     */
    private fun seedBoluses(vararg entries: PumpDbEntryBolus) {
        replayStoredPreferences()
        // The pumpSync members are suspend, so the stubbing needs a coroutine context of its own.
        runBlocking { whenever(pumpSync.addBolusWithTempId(any(), any(), any(), any(), any(), any())) doReturn true }
        entries.forEach { entry ->
            sut.addBolusWithTempId(
                DetailedBolusInfo().also {
                    it.timestamp = entry.date
                    it.insulin = entry.insulin
                    it.carbs = entry.carbs
                    it.bolusType = entry.bolusType
                },
                writeToInternalHistory = true,
                creator = object : PumpSyncEntriesCreator {
                    override fun generateTempId(objectA: Any): Long = entry.temporaryId
                    override fun model(): PumpType = entry.pumpType
                    override fun serialNumber(): String = entry.serialNumber
                }
            )
        }
    }

    private fun seedTbrs(vararg entries: PumpDbEntryTBR) {
        replayStoredPreferences()
        runBlocking { whenever(pumpSync.addTemporaryBasalWithTempId(any(), any(), any(), any(), any(), any(), any(), any())) doReturn true }
        entries.forEach { entry ->
            sut.addTemporaryBasalRateWithTempId(
                PumpDbEntryTBR(entry.rate, entry.isAbsolute, entry.durationInSeconds, entry.tbrType),
                writeToInternalHistory = true,
                creator = object : PumpSyncEntriesCreator {
                    override fun generateTempId(objectA: Any): Long = entry.temporaryId
                    override fun model(): PumpType = entry.pumpType
                    override fun serialNumber(): String = entry.serialNumber
                }
            )
        }
    }

    private fun bolusInfo(insulin: Double = 1.5, carbs: Double = 0.0, timestamp: Long = 1_000L) =
        DetailedBolusInfo().also {
            it.timestamp = timestamp
            it.insulin = insulin
            it.carbs = carbs
            it.bolusType = BS.Type.NORMAL
        }

    @Test fun `no stored preference leaves both lists empty`() {
        assertThat(sut.getBoluses()).isEmpty()
        assertThat(sut.getTBRs()).isEmpty()
    }

    @Test fun `a blank preference is not parsed`() {
        preferences.stub { on { getIfExists(any<StringNonKey>()) } doReturn "   " }

        assertThat(sut.getBoluses()).isEmpty()
    }

    // Unreadable data has to start empty rather than throw: this is the path that records delivered
    // insulin, and taking the driver down with a parse error is the worse of the two. The old XStream
    // XML lands here too, which is why the key was renamed - see StringNonKey.
    @Test fun `unparseable stored data gives an empty list instead of throwing`() {
        preferences.stub { on { getIfExists(any<StringNonKey>()) } doReturn "<not-json" }

        assertThat(sut.getBoluses()).isEmpty()
        assertThat(sut.getTBRs()).isEmpty()
    }

    @Test fun `a saved bolus list is read back with its fields intact`() {
        seedBoluses(
            PumpDbEntryBolus(
                temporaryId = 7L, date = 2_000L, pumpType = PumpType.OMNIPOD_DASH,
                serialNumber = "S", insulin = 2.5, carbs = 0.0, bolusType = BS.Type.SMB
            )
        )

        // A different instance, reading the same store - there is no in-memory copy to carry it over.
        val reloaded = PumpSyncStorage(pumpSync, preferences, aapsLogger).getBoluses()

        assertThat(reloaded).hasSize(1)
        assertThat(reloaded[0].temporaryId).isEqualTo(7L)
        assertThat(reloaded[0].insulin).isEqualTo(2.5)
        assertThat(reloaded[0].bolusType).isEqualTo(BS.Type.SMB)
        assertThat(reloaded[0].serialNumber).isEqualTo("S")
    }

    // Emptying the list REMOVES the key rather than writing an empty document, which is what keeps a
    // cleared queue actually cleared.
    @Test fun `removing the last entry removes the stored key`() {
        seedBoluses(PumpDbEntryBolus(1L, 100L, PumpType.OMNIPOD_DASH, "S", null, 1.0, 0.0, BS.Type.NORMAL))
        assertThat(stored).containsKey(StringNonKey.PumpCommonBolusStorage.key)

        sut.removeBolusWithTemporaryId(1L)

        verify(preferences).remove(StringNonKey.PumpCommonBolusStorage)
        assertThat(stored).doesNotContainKey(StringNonKey.PumpCommonBolusStorage.key)
    }

    /**
     * The reason the in-memory copy went. A caller that mutates what it got back must not change the
     * store behind everyone's back - and, the other way round, must not be able to resurrect an entry
     * that something else removed.
     */
    @Test fun `mutating the returned list does not touch the store`() {
        seedBoluses(PumpDbEntryBolus(1L, 100L, PumpType.OMNIPOD_DASH, "S", null, 1.0, 0.0, BS.Type.NORMAL))

        sut.getBoluses().clear()

        assertThat(sut.getBoluses()).hasSize(1)
    }

    @Test fun `a bolus is stored only when the pump sync accepted it`() = runTest {
        whenever(
            pumpSync.addBolusWithTempId(any(), any(), any(), any(), any(), any())
        ) doReturn false

        val accepted = sut.addBolusWithTempId(bolusInfo(), writeToInternalHistory = true, creator = creator)

        assertThat(accepted).isFalse()
        assertThat(sut.getBoluses()).isEmpty()
    }

    @Test fun `an accepted bolus is stored under the generated temporary id`() = runTest {
        whenever(
            pumpSync.addBolusWithTempId(any(), any(), any(), any(), any(), any())
        ) doReturn true
        creator.nextTempId = 4242L

        val accepted = sut.addBolusWithTempId(bolusInfo(insulin = 3.0), writeToInternalHistory = true, creator = creator)

        assertThat(accepted).isTrue()
        assertThat(sut.getBoluses()).hasSize(1)
        assertThat(sut.getBoluses()[0].temporaryId).isEqualTo(4242L)
        assertThat(sut.getBoluses()[0].insulin).isEqualTo(3.0)
        assertThat(sut.getBoluses()[0].serialNumber).isEqualTo("SERIAL-1")
    }

    // The driver asks for this when it keeps its own history and does not want a second copy here.
    @Test fun `writeToInternalHistory false reports success without storing anything`() = runTest {
        whenever(
            pumpSync.addBolusWithTempId(any(), any(), any(), any(), any(), any())
        ) doReturn true

        val accepted = sut.addBolusWithTempId(bolusInfo(), writeToInternalHistory = false, creator = creator)

        assertThat(accepted).isTrue()
        assertThat(sut.getBoluses()).isEmpty()
    }

    @Test fun `carbs on the bolus are synced separately`() = runTest {
        whenever(
            pumpSync.addBolusWithTempId(any(), any(), any(), any(), any(), any())
        ) doReturn true
        // Must be stubbed: the return is a non-null Boolean, so an unstubbed mock returns null and the
        // unboxing in addCarbs throws before the verify below is ever reached.
        whenever(
            pumpSync.syncCarbsWithTimestamp(any(), any(), anyOrNull(), any(), any())
        ) doReturn true

        sut.addBolusWithTempId(bolusInfo(carbs = 30.0), writeToInternalHistory = false, creator = creator)

        verify(pumpSync).syncCarbsWithTimestamp(eq(1_000L), eq(30.0), anyOrNull(), any(), any())
    }

    @Test fun `a bolus without carbs syncs no carbs`() = runTest {
        whenever(
            pumpSync.addBolusWithTempId(any(), any(), any(), any(), any(), any())
        ) doReturn true

        sut.addBolusWithTempId(bolusInfo(carbs = 0.0), writeToInternalHistory = false, creator = creator)

        verify(pumpSync, never()).syncCarbsWithTimestamp(any(), any(), anyOrNull(), any(), any())
    }

    @Test fun `a temporary basal is stored with its duration converted to milliseconds`() = runTest {
        whenever(
            pumpSync.addTemporaryBasalWithTempId(any(), any(), any(), any(), any(), any(), any(), any())
        ) doReturn true

        val entry = PumpDbEntryTBR(rate = 0.8, isAbsolute = true, durationInSeconds = 1_800, tbrType = PumpSync.TemporaryBasalType.NORMAL)
        val accepted = sut.addTemporaryBasalRateWithTempId(entry, writeToInternalHistory = true, creator = creator)

        assertThat(accepted).isTrue()
        assertThat(sut.getTBRs()).hasSize(1)
        assertThat(sut.getTBRs()[0].durationInSeconds).isEqualTo(1_800)
        // 1800 s must reach pumpSync as 1_800_000 ms; the driver passes seconds, pumpSync wants millis.
        verify(pumpSync).addTemporaryBasalWithTempId(any(), any(), eq(1_800_000L), eq(true), any(), any(), any(), any())
    }

    @Test fun `a rejected temporary basal is not stored`() = runTest {
        whenever(
            pumpSync.addTemporaryBasalWithTempId(any(), any(), any(), any(), any(), any(), any(), any())
        ) doReturn false

        val entry = PumpDbEntryTBR(rate = 1.0, isAbsolute = false, durationInSeconds = 60, tbrType = PumpSync.TemporaryBasalType.NORMAL)

        assertThat(sut.addTemporaryBasalRateWithTempId(entry, writeToInternalHistory = true, creator = creator)).isFalse()
        assertThat(sut.getTBRs()).isEmpty()
    }

    @Test fun `removing by temporary id drops only that bolus`() {
        seedBoluses(
            PumpDbEntryBolus(1L, 100L, PumpType.OMNIPOD_DASH, "S", null, 1.0, 0.0, BS.Type.NORMAL),
            PumpDbEntryBolus(2L, 200L, PumpType.OMNIPOD_DASH, "S", null, 2.0, 0.0, BS.Type.NORMAL)
        )

        sut.removeBolusWithTemporaryId(1L)

        assertThat(sut.getBoluses().map { it.temporaryId }).containsExactly(2L)
    }

    @Test fun `removing an unknown temporary id leaves the list alone`() {
        seedBoluses(PumpDbEntryBolus(1L, 100L, PumpType.OMNIPOD_DASH, "S", null, 1.0, 0.0, BS.Type.NORMAL))

        sut.removeBolusWithTemporaryId(999L)

        assertThat(sut.getBoluses()).hasSize(1)
    }

    @Test fun `removing by temporary id drops only that temporary basal`() {
        seedTbrs(
            PumpDbEntryTBR(1L, 100L, PumpType.OMNIPOD_DASH, "S", null, 0.5, true, 60, PumpSync.TemporaryBasalType.NORMAL),
            PumpDbEntryTBR(2L, 200L, PumpType.OMNIPOD_DASH, "S", null, 1.5, true, 60, PumpSync.TemporaryBasalType.NORMAL)
        )

        sut.removeTemporaryBasalWithTemporaryId(2L)

        assertThat(sut.getTBRs().map { it.temporaryId }).containsExactly(1L)
    }

    @Test fun `the bolus entry built from a DetailedBolusInfo carries its amounts`() {
        val entry = PumpDbEntryBolus(9L, 500L, PumpType.OMNIPOD_DASH, "S", bolusInfo(insulin = 4.0, carbs = 12.0))

        assertThat(entry.insulin).isEqualTo(4.0)
        assertThat(entry.carbs).isEqualTo(12.0)
        assertThat(entry.bolusType).isEqualTo(BS.Type.NORMAL)
        assertThat(entry.pumpId).isNull()
    }

    @Test fun `the carbs entry takes the pump identity from the creator`() {
        val entry = PumpDbEntryCarbs(bolusInfo(carbs = 20.0, timestamp = 777L), creator)

        assertThat(entry.date).isEqualTo(777L)
        assertThat(entry.carbs).isEqualTo(20.0)
        assertThat(entry.pumpType).isEqualTo(PumpType.OMNIPOD_DASH)
        assertThat(entry.serialNumber).isEqualTo("SERIAL-1")
    }

    // The copy constructor is how a bare rate/duration entry becomes a stored one once the pump has
    // accepted it, so the delivery values must survive being re-stamped with identity.
    @Test fun `the temporary basal copy constructor keeps the delivery values`() {
        val bare = PumpDbEntryTBR(rate = 2.25, isAbsolute = false, durationInSeconds = 900, tbrType = PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND)

        val stamped = PumpDbEntryTBR(55L, 1_234L, PumpType.OMNIPOD_DASH, "S2", bare, pumpId = 99L)

        assertThat(stamped.rate).isEqualTo(2.25)
        assertThat(stamped.isAbsolute).isFalse()
        assertThat(stamped.durationInSeconds).isEqualTo(900)
        assertThat(stamped.tbrType).isEqualTo(PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND)
        assertThat(stamped.temporaryId).isEqualTo(55L)
        assertThat(stamped.pumpId).isEqualTo(99L)
    }
}
