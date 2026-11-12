package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseSample

/**
 * Short history pull after session up.
 *
 * Production path: [OnePlusBackfillSession] invoked from [OnePlusEgvSession] after
 * TransmitterTime / SessionStart. This interface remains for tests / stubs.
 */
interface OnePlusBackfill {
    fun requestRecent(): List<OnePlusGlucoseSample>
}

class OnePlusBackfillStub : OnePlusBackfill {
    override fun requestRecent(): List<OnePlusGlucoseSample> = emptyList()
}
