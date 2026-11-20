package app.aaps.pump.aaruy.services

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.compose.AaruyHistoryTypes
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Whether a change of reservoir is still going on, read from the pump log, which comes newest first. */
class AaruyReplaceStepsLogTest {

    private val dao: HistoryUnitInfoDao = mock()
    private val steps = AaruyReplaceSteps(mock<AaruyBLE>(), mock<AaruyPump>(), mock<DateUtil>(), dao)
    private var minute = 0L

    private fun entry(type: Byte) = HistoryUnitInfo().also {
        it.time = minute--
        it.type = type.toInt()
    }

    private fun logIs(vararg newestFirst: HistoryUnitInfo): Boolean {
        whenever(dao.querySince(any())).thenReturn(newestFirst.toList())
        return runBlocking { steps.rewoundWithoutDeliverySince() }
    }

    @Test
    fun aRewindWithNothingAfterItGoesOn() {
        assertTrue(logIs(entry(AaruyHistoryTypes.REWIND), entry(AaruyHistoryTypes.BASAL)))
    }

    /** A real pump writes its prime entry only when it leaves the stop, so the entry says nothing here. */
    @Test
    fun aPrimeAfterTheRewindGoesOn() {
        assertTrue(logIs(entry(AaruyHistoryTypes.PRIME), entry(AaruyHistoryTypes.REWIND)))
    }

    /** As "stopped for more than 15 minutes": the change goes on. */
    @Test
    fun anAlarmDoesNotEndTheChange() {
        assertTrue(logIs(entry(AaruyHistoryTypes.ALARM), entry(AaruyHistoryTypes.REWIND)))
    }

    /** Basal, a temp basal or a bolus after it: that change is over. */
    @Test
    fun insulinGivenAfterTheRewindEndsTheChange() {
        for (type in listOf(AaruyHistoryTypes.BASAL, AaruyHistoryTypes.TEMP_BASAL, AaruyHistoryTypes.BOLUS))
            assertFalse(logIs(entry(type), entry(AaruyHistoryTypes.PRIME), entry(AaruyHistoryTypes.REWIND)), "after type $type")
    }

    @Test
    fun noRewindIsNoChange() {
        assertFalse(logIs(entry(AaruyHistoryTypes.PRIME), entry(AaruyHistoryTypes.BASAL)))
        assertFalse(logIs())
    }
}
