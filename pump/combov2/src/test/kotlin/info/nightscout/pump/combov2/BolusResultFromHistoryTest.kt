package info.nightscout.pump.combov2

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import info.nightscout.comboctl.main.Pump as ComboCtlPump

/**
 * When an alert screen, a cancel or another error takes the place of a bolus's own result, the
 * history read after the bolus decides what the user is told (#5377). A bolus given in full and
 * then followed by an alert screen used to be "It appears no bolus was delivered".
 */
class BolusResultFromHistoryTest {

    private val read = ComboCtlPump.BolusOutcome.HistoryRead(cancelledByUser = false)

    @Test
    fun theWholeBolusInTheHistoryIsGiven() {
        assertThat(bolusResultFromHistory(givenAmount = 25, requestedAmount = 25, read)).isEqualTo(BolusResultFromHistory.GIVEN)
    }

    @Test
    fun theWholeBolusWithOtherBolusesStillSaysSo() {
        assertThat(bolusResultFromHistory(25, 25, read.copy(otherBolusesFound = true))).isEqualTo(BolusResultFromHistory.GIVEN_WITH_OTHER_BOLUSES)
    }

    @Test
    fun aCancelIsACancelAlsoAfterPartOfTheBolus() {
        val cancelled = read.copy(cancelledByUser = true)
        assertThat(bolusResultFromHistory(10, 25, cancelled)).isEqualTo(BolusResultFromHistory.CANCELLED)
        assertThat(bolusResultFromHistory(0, 25, cancelled)).isEqualTo(BolusResultFromHistory.CANCELLED)
    }

    @Test
    fun partOfTheBolusWithoutACancelIsStoppedEarly() {
        assertThat(bolusResultFromHistory(10, 25, read)).isEqualTo(BolusResultFromHistory.STOPPED_EARLY)
    }

    @Test
    fun nothingInTheHistoryIsNotGiven() {
        assertThat(bolusResultFromHistory(0, 25, read)).isEqualTo(BolusResultFromHistory.NOT_GIVEN)
    }
}
