package app.aaps.implementation.pump

import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
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
}
