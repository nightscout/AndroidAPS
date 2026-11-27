package app.aaps.workflow

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A calculation runs for every glucose reading, so an error that comes from stored data comes back
 * every 5 minutes. Crashlytics gets each kind once: enough to find it, without the same error filling
 * the reports.
 */
class CalculationErrorReportsTest {

    private val fabricPrivacy = RecordingFabricPrivacy()
    private val sut = CalculationErrorReports(fabricPrivacy)

    @Test
    fun `the message goes to the Crashlytics log just before the error`() {
        val error = IllegalStateException("broken")

        sut.report("Graph step prepareBgData failed", error)

        assertEquals(listOf<Any>("Graph step prepareBgData failed", error), fabricPrivacy.sent)
    }

    @Test
    fun `the same error in the same place is sent once`() {
        val first = IllegalStateException("broken")

        sut.report("Graph step prepareBgData failed", first)
        sut.report("Graph step prepareBgData failed", IllegalStateException("broken again"))

        assertEquals(listOf<Throwable>(first), fabricPrivacy.exceptions)
    }

    @Test
    fun `another type of error in the same place is still sent`() {
        val first = IllegalStateException("broken")
        val second = IndexOutOfBoundsException("index 3")

        sut.report("Graph step prepareBgData failed", first)
        sut.report("Graph step prepareBgData failed", second)

        assertEquals(listOf<Throwable>(first, second), fabricPrivacy.exceptions)
    }

    @Test
    fun `the same type of error in another place is still sent`() {
        val first = IllegalStateException("broken")
        val second = IllegalStateException("broken")

        sut.report("Graph step prepareBgData failed", first)
        sut.report("Graph step prepareBucketedData failed", second)

        assertEquals(listOf<Throwable>(first, second), fabricPrivacy.exceptions)
    }
}
