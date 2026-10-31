package app.aaps.implementation.pump

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The first record of a new pump registers the pump. The records that follow must get the same time limit,
 * or a record with the same time as the first one is dropped as "older history".
 */
class PumpSyncNewPumpTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var activePump: PumpWithConcentration

    private lateinit var sut: PumpSyncImplementation

    private val pumpType = PumpType.EQUIL
    private val pumpSerial = "Equil - 7000F3"
    private val now = 1000000000L

    // What the registration stores, kept here because Preferences is a mock
    private val stored = mutableMapOf<StringNonKey, String>()
    private var storedChangeTimestamp = 0L

    @BeforeEach
    fun setup() {
        stored.clear()
        storedChangeTimestamp = 0L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(activePlugin.activePump).thenReturn(activePump)
        whenever(activePump.selectedActivePump()).thenReturn(activePump)

        for (key in listOf(StringNonKey.ActivePumpType, StringNonKey.ActivePumpSerialNumber)) {
            whenever(preferences.get(key)).thenAnswer { stored[key] ?: "" }
            doAnswer { stored[key] = it.getArgument(1) }.whenever(preferences).put(eq(key), any<String>())
        }
        whenever(preferences.get(LongNonKey.ActivePumpChangeTimestamp)).thenAnswer { storedChangeTimestamp }
        doAnswer { storedChangeTimestamp = it.getArgument(1) }.whenever(preferences).put(eq(LongNonKey.ActivePumpChangeTimestamp), any<Long>())

        runBlocking {
            whenever(persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), anyOrNull(), any())).thenAnswer {
                PersistenceLayer.TransactionResult<TE>().apply { inserted.add(it.getArgument(0)) }
            }
        }

        sut = PumpSyncImplementation(aapsLogger, dateUtil, preferences, notificationManager, profileFunction, persistenceLayer, activePlugin)
    }

    private suspend fun insert(timestamp: Long, type: TE.Type) =
        sut.insertTherapyEventIfNewWithTimestamp(timestamp, type, null, null, pumpType, pumpSerial)

    @Test
    fun `pairing writes cannula change and insulin change with the same time, both are kept`() {
        runBlocking {
            // As on a real Equil: both records carry the wizard's time, a few ms before the registration
            val time = now - 37

            assertThat(insert(time, TE.Type.CANNULA_CHANGE)).isTrue()
            assertThat(insert(time, TE.Type.INSULIN_CHANGE)).isTrue()
        }
    }

    @Test
    fun `history older than 1 min before the registration is still ignored`() {
        runBlocking {
            assertThat(insert(now - 10, TE.Type.CANNULA_CHANGE)).isTrue()

            assertThat(insert(now - 61_000, TE.Type.INSULIN_CHANGE)).isFalse()
        }
    }

    @Test
    fun `a first record older than 1 min registers the pump but is ignored`() {
        runBlocking {
            assertThat(insert(now - 61_000, TE.Type.CANNULA_CHANGE)).isFalse()

            assertThat(stored[StringNonKey.ActivePumpSerialNumber]).isEqualTo(pumpSerial)
            assertThat(insert(now, TE.Type.INSULIN_CHANGE)).isTrue()
        }
    }

    // ---- a pump change: what still runs on the new pump, and what the old one left ----

    private val registeredFrom = now - 60_000
    private val minute = 60_000L

    private suspend fun registerPump() {
        insert(now, TE.Type.CANNULA_CHANGE)
        assertThat(storedChangeTimestamp).isEqualTo(registeredFrom)
    }

    private suspend fun storedTemporaryBasals(recordedBefore: List<TB> = emptyList()): List<TB> {
        val profile = mock<EffectiveProfile>().also { whenever(it.insulinConcentration()).thenReturn(1.0) }
        whenever(profileFunction.getProfile(any<Long>())).thenReturn(profile)
        whenever(persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(any(), any(), any())).thenReturn(recordedBefore)
        val stored = mutableListOf<TB>()
        whenever(persistenceLayer.syncPumpTemporaryBasal(any(), anyOrNull())).thenAnswer {
            stored.add(it.getArgument(0))
            PersistenceLayer.TransactionResult<TB>().apply { inserted.add(it.getArgument(0)) }
        }
        return stored
    }

    /** It used to be dropped as older history, and AAPS showed no temp basal while the pump ran one. */
    @Test
    fun `a temp basal still running when the pump is registered is taken from the registration`() = runBlocking<Unit> {
        registerPump()
        val stored = storedTemporaryBasals()

        val taken = sut.syncTemporaryBasalWithPumpId(now - 30 * minute, PumpRate(0.0), 60 * minute, true, null, 7L, pumpType, pumpSerial)

        assertThat(taken).isTrue()
        assertThat(stored.single().timestamp).isEqualTo(registeredFrom)
        // 60 min from 30 min ago is 30 more min; from the registration 1 min ago that is 31 min.
        assertThat(stored.single().duration).isEqualTo(31 * minute)
        assertThat(stored.single().ids.pumpId).isEqualTo(7L)
    }

    /** The first record of the new pump can be the running temp basal itself: it registers the pump too. */
    @Test
    fun `a running temp basal as the first record registers the pump and is taken from then`() = runBlocking<Unit> {
        val stored = storedTemporaryBasals()

        assertThat(sut.syncTemporaryBasalWithPumpId(now - 30 * minute, PumpRate(0.0), 60 * minute, true, null, 7L, pumpType, pumpSerial)).isTrue()

        assertThat(stored.single().timestamp).isEqualTo(registeredFrom)
    }

    /**
     * This pump was used, then another one, and now this one again, while its temp basal still runs. The entry from
     * the first time ended at the change of pump. Moved to the new registration, what it gave before would be lost.
     */
    @Test
    fun `a running temp basal recorded under an earlier registration is left as it is`() = runBlocking<Unit> {
        registerPump()
        val recordedBefore = TB(
            timestamp = now - 30 * minute, rate = 0.0, duration = 10 * minute, isAbsolute = true, type = TB.Type.NORMAL,
            ids = IDs(pumpId = 7L, pumpType = pumpType, pumpSerial = pumpSerial)
        )
        val stored = storedTemporaryBasals(recordedBefore = listOf(recordedBefore))

        assertThat(sut.syncTemporaryBasalWithPumpId(now - 30 * minute, PumpRate(0.0), 60 * minute, true, null, 7L, pumpType, pumpSerial)).isFalse()

        assertThat(stored).isEmpty()
        // Up to just before the registration: what was taken from the registration itself may be updated.
        verify(persistenceLayer).getTemporaryBasalsStartingFromTimeToTime(now - 30 * minute, registeredFrom - 1, true)
    }

    /** Another record of the same time does not stop it: only an entry with the same pump id does. */
    @Test
    fun `a running temp basal is taken although another pump's entry started before the registration`() = runBlocking<Unit> {
        registerPump()
        val oldPumps = TB(
            timestamp = now - 30 * minute, rate = 0.0, duration = 10 * minute, isAbsolute = true, type = TB.Type.NORMAL,
            ids = IDs(pumpId = 7L, pumpType = PumpType.MEDTRONIC_522_722, pumpSerial = "678734")
        )
        val stored = storedTemporaryBasals(recordedBefore = listOf(oldPumps))

        assertThat(sut.syncTemporaryBasalWithPumpId(now - 30 * minute, PumpRate(0.0), 60 * minute, true, null, 7L, pumpType, pumpSerial)).isTrue()

        assertThat(stored.single().timestamp).isEqualTo(registeredFrom)
    }

    @Test
    fun `a temp basal that ended before the registration is not taken`() = runBlocking<Unit> {
        registerPump()
        val stored = storedTemporaryBasals()

        assertThat(sut.syncTemporaryBasalWithPumpId(now - 90 * minute, PumpRate(0.0), 60 * minute, true, null, 7L, pumpType, pumpSerial)).isFalse()
        assertThat(stored).isEmpty()
    }

    @Test
    fun `a running temp basal of another pump is not taken`() = runBlocking<Unit> {
        registerPump()
        val stored = storedTemporaryBasals()

        assertThat(sut.syncTemporaryBasalWithPumpId(now - 30 * minute, PumpRate(0.0), 60 * minute, true, null, 7L, pumpType, "another serial")).isFalse()
        assertThat(stored).isEmpty()
    }

    /**
     * Pumps that read their history send the start of a temp basal with its whole length, and its stop as another
     * record. Without this, a temp basal stopped before the registration stayed in AAPS as running.
     */
    @Test
    fun `a stop from before the registration ends the temp basal taken from the registration`() = runBlocking<Unit> {
        registerPump()
        val takenFromRegistration = TB(
            timestamp = registeredFrom, rate = 0.0, duration = 31 * minute, isAbsolute = true, type = TB.Type.NORMAL,
            ids = IDs(pumpId = 7L, pumpType = pumpType, pumpSerial = pumpSerial)
        )
        whenever(persistenceLayer.getTemporaryBasalActiveAt(registeredFrom)).thenReturn(takenFromRegistration)
        whenever(persistenceLayer.syncPumpCancelTemporaryBasalIfAny(any(), any(), any(), any())).thenReturn(PersistenceLayer.TransactionResult())

        sut.syncStopTemporaryBasalWithPumpId(now - 20 * minute, 8L, pumpType, pumpSerial)

        verify(persistenceLayer).syncPumpCancelTemporaryBasalIfAny(registeredFrom, 8L, pumpType, pumpSerial)
    }

    /** The old pump's temp basal can still run at the registration, ended at the switch a few seconds later. */
    @Test
    fun `a stop from before the registration does not touch a temp basal of the old pump`() = runBlocking<Unit> {
        registerPump()
        val oldPumps = TB(
            timestamp = now - 30 * minute, rate = 0.0, duration = 60 * minute, isAbsolute = true, type = TB.Type.NORMAL,
            ids = IDs(pumpId = 5L, pumpType = PumpType.MEDTRONIC_522_722, pumpSerial = "678734")
        )
        whenever(persistenceLayer.getTemporaryBasalActiveAt(registeredFrom)).thenReturn(oldPumps)

        assertThat(sut.syncStopTemporaryBasalWithPumpId(now - 20 * minute, 8L, pumpType, pumpSerial)).isFalse()
        verify(persistenceLayer, never()).syncPumpCancelTemporaryBasalIfAny(any(), any(), any(), any())
    }

    @Test
    fun `an extended bolus still running when the pump is registered is taken from then, with the rest of its insulin`() = runBlocking<Unit> {
        registerPump()
        val profile = mock<EffectiveProfile>().also { whenever(it.insulinConcentration()).thenReturn(1.0) }
        whenever(profileFunction.getProfile(any<Long>())).thenReturn(profile)
        whenever(persistenceLayer.getExtendedBolusesStartingFromTimeToTime(any(), any(), any())).thenReturn(emptyList())
        val stored = mutableListOf<EB>()
        whenever(persistenceLayer.syncPumpExtendedBolus(any())).thenAnswer {
            stored.add(it.getArgument(0))
            PersistenceLayer.TransactionResult<EB>().apply { inserted.add(it.getArgument(0)) }
        }

        // 2 U over 60 min, started 30 min ago: from the registration 1 min ago, 31 min and 31/60 of the insulin are left.
        assertThat(sut.syncExtendedBolusWithPumpId(now - 30 * minute, PumpRate(2.0), 60 * minute, false, 9L, pumpType, pumpSerial)).isTrue()

        assertThat(stored.single().timestamp).isEqualTo(registeredFrom)
        assertThat(stored.single().duration).isEqualTo(31 * minute)
        assertThat(stored.single().amount).isWithin(1e-9).of(2.0 * 31 / 60)
    }

    @Test
    fun `a running extended bolus recorded under an earlier registration is left as it is`() = runBlocking<Unit> {
        registerPump()
        val recordedBefore = EB(
            timestamp = now - 30 * minute, amount = 2.0, duration = 10 * minute, isEmulatingTempBasal = false,
            ids = IDs(pumpId = 9L, pumpType = pumpType, pumpSerial = pumpSerial)
        )
        whenever(persistenceLayer.getExtendedBolusesStartingFromTimeToTime(any(), any(), any())).thenReturn(listOf(recordedBefore))

        assertThat(sut.syncExtendedBolusWithPumpId(now - 30 * minute, PumpRate(2.0), 60 * minute, false, 9L, pumpType, pumpSerial)).isFalse()

        verify(persistenceLayer, never()).syncPumpExtendedBolus(any())
    }

    /** The old pump does not confirm them any more, and a record without a pump id never reaches Nightscout. */
    @Test
    fun `a pump change keeps the records that have only a temporary id`() = runBlocking<Unit> {
        whenever(persistenceLayer.getTemporaryBasalActiveAt(any())).thenReturn(null)
        whenever(persistenceLayer.getExtendedBolusActiveAt(any())).thenReturn(null)
        whenever(persistenceLayer.getNewestBolus()).thenReturn(null)
        whenever(persistenceLayer.confirmTemporaryIds()).thenReturn(2)

        sut.connectNewPump(endRunning = true)

        verify(persistenceLayer).confirmTemporaryIds()
    }

    /** Called only to accept data again, not for a pump change. */
    @Test
    fun `without ending what runs, the records with a temporary id are left as they are`() = runBlocking<Unit> {
        sut.connectNewPump(endRunning = false)

        verify(persistenceLayer, never()).confirmTemporaryIds()
    }
}
