package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressReporterTest : TestBase() {
    // NOTE: In the tests here, the progress sequences are fairly
    // arbitrary, and do _not_ reflect how actual sequences used
    // in pairing etc. look like.

    @Test
    fun testBasicProgress() {
        val progressReporter = ProgressReporter<Unit>(
            listOf(
                BasicProgressStage.EstablishingBtConnection::class,
                BasicProgressStage.PerformingConnectionHandshake::class,
                BasicProgressStage.ComboPairingKeyAndPinRequested::class,
                BasicProgressStage.ComboPairingFinishing::class
            ),
            Unit
        )

        assertEquals(
            ProgressReport(0, 4, BasicProgressStage.Idle, 0.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(
            BasicProgressStage.EstablishingBtConnection(1, 3)
        )
        assertEquals(
            ProgressReport(0, 4, BasicProgressStage.EstablishingBtConnection(1, 3), 0.0 / 4.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(
            BasicProgressStage.EstablishingBtConnection(2, 3)
        )
        assertEquals(
            ProgressReport(0, 4, BasicProgressStage.EstablishingBtConnection(2, 3), 0.0 / 4.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(BasicProgressStage.PerformingConnectionHandshake)
        assertEquals(
            ProgressReport(1, 4, BasicProgressStage.PerformingConnectionHandshake, 1.0 / 4.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(BasicProgressStage.ComboPairingKeyAndPinRequested)
        assertEquals(
            ProgressReport(2, 4, BasicProgressStage.ComboPairingKeyAndPinRequested, 2.0 / 4.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(BasicProgressStage.ComboPairingFinishing)
        assertEquals(
            ProgressReport(3, 4, BasicProgressStage.ComboPairingFinishing, 3.0 / 4.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(BasicProgressStage.Finished)
        assertEquals(
            ProgressReport(4, 4, BasicProgressStage.Finished, 4.0 / 4.0),
            progressReporter.progressFlow.value
        )
    }

    @Test
    fun testSkippedSteps() {
        val progressReporter = ProgressReporter<Unit>(
            listOf(
                BasicProgressStage.EstablishingBtConnection::class,
                BasicProgressStage.PerformingConnectionHandshake::class,
                BasicProgressStage.ComboPairingKeyAndPinRequested::class,
                BasicProgressStage.ComboPairingFinishing::class
            ),
            Unit
        )

        assertEquals(
            ProgressReport(0, 4, BasicProgressStage.Idle, 0.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(
            BasicProgressStage.EstablishingBtConnection(1, 3)
        )
        assertEquals(
            ProgressReport(0, 4, BasicProgressStage.EstablishingBtConnection(1, 3), 0.0 / 4.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(BasicProgressStage.ComboPairingFinishing)
        assertEquals(
            ProgressReport(3, 4, BasicProgressStage.ComboPairingFinishing, 3.0 / 4.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(BasicProgressStage.Finished)
        assertEquals(
            ProgressReport(4, 4, BasicProgressStage.Finished, 4.0 / 4.0),
            progressReporter.progressFlow.value
        )
    }

    @Test
    fun testBackwardsProgress() {
        val progressReporter = ProgressReporter<Unit>(
            listOf(
                BasicProgressStage.EstablishingBtConnection::class,
                BasicProgressStage.PerformingConnectionHandshake::class,
                BasicProgressStage.ComboPairingKeyAndPinRequested::class,
                BasicProgressStage.ComboPairingFinishing::class
            ),
            Unit
        )

        assertEquals(
            ProgressReport(0, 4, BasicProgressStage.Idle, 0.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(BasicProgressStage.ComboPairingFinishing)
        assertEquals(
            ProgressReport(3, 4, BasicProgressStage.ComboPairingFinishing, 3.0 / 4.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(
            BasicProgressStage.EstablishingBtConnection(1, 3)
        )
        assertEquals(
            ProgressReport(0, 4, BasicProgressStage.EstablishingBtConnection(1, 3), 0.0 / 4.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(BasicProgressStage.Finished)
        assertEquals(
            ProgressReport(4, 4, BasicProgressStage.Finished, 4.0 / 4.0),
            progressReporter.progressFlow.value
        )
    }

    @Test
    fun testAbort() {
        val progressReporter = ProgressReporter<Unit>(
            listOf(
                BasicProgressStage.EstablishingBtConnection::class,
                BasicProgressStage.PerformingConnectionHandshake::class,
                BasicProgressStage.ComboPairingKeyAndPinRequested::class,
                BasicProgressStage.ComboPairingFinishing::class
            ),
            Unit
        )

        assertEquals(
            ProgressReport(0, 4, BasicProgressStage.Idle, 0.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(
            BasicProgressStage.EstablishingBtConnection(1, 3)
        )
        assertEquals(
            ProgressReport(0, 4, BasicProgressStage.EstablishingBtConnection(1, 3), 0.0 / 4.0),
            progressReporter.progressFlow.value
        )

        progressReporter.setCurrentProgressStage(BasicProgressStage.Cancelled)
        assertEquals(
            ProgressReport(4, 4, BasicProgressStage.Cancelled, 4.0 / 4.0),
            progressReporter.progressFlow.value
        )
    }
}
