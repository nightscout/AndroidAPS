package app.aaps.pump.aaruy.services

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.common.enums.AaruyReplaceProgress
import app.aaps.pump.aaruy.compose.AaruyHistoryTypes
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** How far a change of reservoir came, read from the pump log, which comes newest first. */
class AaruyReplaceProgressTest {

    private val dao: HistoryUnitInfoDao = mock()
    private val steps = AaruyReplaceSteps(mock<AaruyBLE>(), mock<AaruyPump>(), mock<DateUtil>(), dao)
    private var minute = 0L

    private fun entry(type: Byte) = HistoryUnitInfo().also {
        it.time = minute--
        it.type = type.toInt()
    }

    private fun logIs(vararg newestFirst: HistoryUnitInfo): AaruyReplaceProgress {
        whenever(dao.querySince(any())).thenReturn(newestFirst.toList())
        return runBlocking { steps.progressFromLog() }
    }

    @Test
    fun aRewindAloneIsRewound() {
        assertEquals(AaruyReplaceProgress.REWOUND, logIs(entry(AaruyHistoryTypes.REWIND), entry(AaruyHistoryTypes.BASAL)))
    }

    @Test
    fun aPrimeAfterTheRewindIsPrimed() {
        assertEquals(AaruyReplaceProgress.PRIMED, logIs(entry(AaruyHistoryTypes.PRIME), entry(AaruyHistoryTypes.REWIND)))
    }

    /** As "stopped for more than 15 minutes": the change goes on. */
    @Test
    fun anAlarmDoesNotEndTheChange() {
        assertEquals(AaruyReplaceProgress.PRIMED, logIs(entry(AaruyHistoryTypes.ALARM), entry(AaruyHistoryTypes.PRIME), entry(AaruyHistoryTypes.REWIND)))
    }

    /** Basal, a temp basal or a bolus after it: that change is over. */
    @Test
    fun insulinGivenAfterTheRewindEndsTheChange() {
        for (type in listOf(AaruyHistoryTypes.BASAL, AaruyHistoryTypes.TEMP_BASAL, AaruyHistoryTypes.BOLUS))
            assertEquals(AaruyReplaceProgress.NONE, logIs(entry(type), entry(AaruyHistoryTypes.PRIME), entry(AaruyHistoryTypes.REWIND)))
    }

    /** It primed the reservoir before, not the one of this rewind. */
    @Test
    fun aPrimeBeforeTheRewindIsNotOfThisChange() {
        assertEquals(AaruyReplaceProgress.REWOUND, logIs(entry(AaruyHistoryTypes.REWIND), entry(AaruyHistoryTypes.PRIME)))
    }

    @Test
    fun noRewindIsNoChange() {
        assertEquals(AaruyReplaceProgress.NONE, logIs(entry(AaruyHistoryTypes.PRIME), entry(AaruyHistoryTypes.BASAL)))
        assertEquals(AaruyReplaceProgress.NONE, logIs())
    }
}
