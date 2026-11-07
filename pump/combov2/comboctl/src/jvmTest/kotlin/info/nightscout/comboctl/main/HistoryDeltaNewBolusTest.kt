package info.nightscout.comboctl.main

import info.nightscout.comboctl.base.ApplicationLayer.CMDHistoryEvent
import info.nightscout.comboctl.base.ApplicationLayer.CMDHistoryEventDetail
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// containsNewBolus() decides whether a bolus is given or refused (#5359).
class HistoryDeltaNewBolusTest {

    private fun event(detail: CMDHistoryEventDetail, counter: Long = 1) =
        CMDHistoryEvent(LocalDateTime(2026, 10, 22, 18, 1, 38), counter, detail)

    @Test
    fun emptyDeltaHasNoNewBolus() {
        assertFalse(emptyList<CMDHistoryEvent>().containsNewBolus())
    }

    @Test
    fun infusedStandardBolusIsNew() {
        val delta = listOf(
            event(CMDHistoryEventDetail.StandardBolusRequested(bolusAmount = 90, manual = false), 1),
            event(CMDHistoryEventDetail.StandardBolusInfused(bolusAmount = 90, manual = false), 2)
        )
        assertTrue(delta.containsNewBolus())
    }

    // An earlier read can take the "requested" entry while the bolus runs, so a delta can have
    // the "infused" entry alone. The read before a bolus is there for this case (#5377).
    @Test
    fun infusedStandardBolusAloneIsNew() {
        assertTrue(listOf(event(CMDHistoryEventDetail.StandardBolusInfused(bolusAmount = 90, manual = false))).containsNewBolus())
    }

    @Test
    fun requestedBolusWithoutInfusedIsStillRunningAndCounts() {
        assertTrue(listOf(event(CMDHistoryEventDetail.StandardBolusRequested(bolusAmount = 150, manual = false))).containsNewBolus())
        assertTrue(listOf(event(CMDHistoryEventDetail.QuickBolusRequested(bolusAmount = 20))).containsNewBolus())
    }

    @Test
    fun quickBolusFromThePumpButtonsIsNew() {
        assertTrue(listOf(event(CMDHistoryEventDetail.QuickBolusInfused(bolusAmount = 20))).containsNewBolus())
    }

    @Test
    fun startOfExtendedOrMultiwaveBolusIsNew() {
        assertTrue(listOf(event(CMDHistoryEventDetail.ExtendedBolusStarted(totalBolusAmount = 20, totalDurationMinutes = 30, manual = true))).containsNewBolus())
        assertTrue(
            listOf(
                event(CMDHistoryEventDetail.MultiwaveBolusStarted(totalBolusAmount = 30, immediateBolusAmount = 10, totalDurationMinutes = 30, manual = true))
            ).containsNewBolus()
        )
    }

    @Test
    fun endOfExtendedOrMultiwaveBolusIsNotNew() {
        val delta = listOf(
            event(CMDHistoryEventDetail.ExtendedBolusEnded(totalBolusAmount = 20, totalDurationMinutes = 30, manual = true), 1),
            event(CMDHistoryEventDetail.MultiwaveBolusEnded(totalBolusAmount = 30, immediateBolusAmount = 10, totalDurationMinutes = 30, manual = true), 2)
        )
        assertFalse(delta.containsNewBolus())
    }

    @Test
    fun dateTimeChangeIsNotABolus() {
        assertFalse(listOf(event(CMDHistoryEventDetail.NewDateTimeSet(LocalDateTime(2026, 10, 22, 18, 0, 0)))).containsNewBolus())
    }
}
