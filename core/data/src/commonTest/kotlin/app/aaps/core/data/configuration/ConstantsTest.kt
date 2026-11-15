package app.aaps.core.data.configuration

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The normal target is 99 mg/dL, and the comparison rounds first: 5.5 mmol/L is 99.09 mg/dL with the
 * 18.01559 factor and 99.0 with the old 18.0 factor, and both must be the normal target, neither high
 * nor low. In `commonTest` so it runs on every target, the loop runs on all of them.
 */
class ConstantsTest {

    private fun mmol(value: Double) = value * Constants.MMOLL_TO_MGDL

    @Test
    fun `5_5 mmol is the normal target with either unit factor`() {
        for (target in listOf(mmol(5.5), 5.5 * 18.0, 99.0)) {
            assertFalse(Constants.isAboveNormalTarget(target), "$target is not high")
            assertFalse(Constants.isBelowNormalTarget(target), "$target is not low")
        }
    }

    @Test
    fun `5_6 mmol and 100 mg_dL are high`() {
        assertTrue(Constants.isAboveNormalTarget(mmol(5.6)))
        assertTrue(Constants.isAboveNormalTarget(5.6 * 18.0))
        assertTrue(Constants.isAboveNormalTarget(100.0))
        assertFalse(Constants.isBelowNormalTarget(100.0))
    }

    @Test
    fun `5_4 mmol and 98 mg_dL are low`() {
        assertTrue(Constants.isBelowNormalTarget(mmol(5.4)))
        assertTrue(Constants.isBelowNormalTarget(5.4 * 18.0))
        assertTrue(Constants.isBelowNormalTarget(98.0))
        assertFalse(Constants.isAboveNormalTarget(98.0))
    }

    /** A target that is not a number is neither high nor low, so no branch that changes the dose is entered. */
    @Test
    fun `a non-finite target is neither high nor low`() {
        assertFalse(Constants.isAboveNormalTarget(Double.NaN))
        assertFalse(Constants.isBelowNormalTarget(Double.NaN))
    }
}
