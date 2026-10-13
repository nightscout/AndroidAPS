package app.aaps.pump.eopatch

import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.disposables.Disposable
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.*

class CommonUtilsTest {

    @Test
    fun `hasText should return false for null string`() {
        assertThat(CommonUtils.hasText(null as String?)).isFalse()
    }

    @Test
    fun `hasText should return false for empty string`() {
        assertThat(CommonUtils.hasText("")).isFalse()
    }

    @Test
    fun `hasText should return false for whitespace only string`() {
        assertThat(CommonUtils.hasText("   ")).isFalse()
        assertThat(CommonUtils.hasText("\t\n")).isFalse()
    }

    @Test
    fun `hasText should return true for non-empty string`() {
        assertThat(CommonUtils.hasText("test")).isTrue()
        assertThat(CommonUtils.hasText(" test ")).isTrue()
        assertThat(CommonUtils.hasText("a")).isTrue()
    }

    @Test
    fun `hasText CharSequence should return false for null`() {
        assertThat(CommonUtils.hasText(null as CharSequence?)).isFalse()
    }

    @Test
    fun `hasText CharSequence should return false for empty`() {
        assertThat(CommonUtils.hasText("" as CharSequence)).isFalse()
    }

    @Test
    fun `hasText CharSequence should return true for non-empty`() {
        assertThat(CommonUtils.hasText("test" as CharSequence)).isTrue()
    }

    @Test
    fun `isStringEmpty should return true for null`() {
        assertThat(CommonUtils.isStringEmpty(null)).isTrue()
    }

    @Test
    fun `isStringEmpty should return true for empty string`() {
        assertThat(CommonUtils.isStringEmpty("")).isTrue()
    }

    @Test
    fun `isStringEmpty should return false for non-empty string`() {
        assertThat(CommonUtils.isStringEmpty("test")).isFalse()
        assertThat(CommonUtils.isStringEmpty(" ")).isFalse()
    }

    @Test
    fun `dateString should format millis correctly`() {
        // January 1, 2023, 12:30:45
        val calendar = Calendar.getInstance()
        calendar.set(2023, Calendar.JANUARY, 1, 12, 30, 45)
        calendar.set(Calendar.MILLISECOND, 0)

        val dateStr = CommonUtils.dateString(calendar.timeInMillis)

        assertThat(dateStr).isEqualTo("2023-01-01 12:30:45")
    }

    @Test
    fun `dateString should return empty for zero millis`() {
        assertThat(CommonUtils.dateString(0L)).isEmpty()
    }

    @Test
    fun `dateString with calendar should format correctly`() {
        val calendar = Calendar.getInstance()
        calendar.set(2023, Calendar.DECEMBER, 31, 23, 59, 59)

        val dateStr = CommonUtils.dateString(calendar)

        assertThat(dateStr).isEqualTo("2023-12-31 23:59:59")
    }

    @Test
    fun `getRemainHourMin should calculate hours and minutes correctly`() {
        // 2 hours and 30 minutes = 150 minutes = 9000000 milliseconds
        val timeMillis = 2 * 60 * 60 * 1000L + 30 * 60 * 1000L

        val (hours, minutes) = CommonUtils.getRemainHourMin(timeMillis)

        assertThat(hours).isEqualTo(2)
        assertThat(minutes).isEqualTo(31) // +1 minute due to logic
    }

    @Test
    fun `getRemainHourMin should handle exact hours`() {
        // Exactly 1 hour
        val timeMillis = 1 * 60 * 60 * 1000L

        val (hours, minutes) = CommonUtils.getRemainHourMin(timeMillis)

        assertThat(hours).isEqualTo(1)
        assertThat(minutes).isEqualTo(1) // +1 minute due to logic
    }

    @Test
    fun `getRemainHourMin should handle negative time`() {
        val timeMillis = -30 * 60 * 1000L // -30 minutes

        val (hours, minutes) = CommonUtils.getRemainHourMin(timeMillis)

        assertThat(hours).isEqualTo(0)
        assertThat(minutes).isEqualTo(30)
    }

    @Test
    fun `getRemainHourMin should handle zero time`() {
        val (hours, minutes) = CommonUtils.getRemainHourMin(0L)

        assertThat(hours).isEqualTo(0)
        assertThat(minutes).isEqualTo(1) // +1 minute due to logic
    }

    @Test
    fun `nearlyEqual should return true for equal floats`() {
        assertThat(CommonUtils.nearlyEqual(1.0f, 1.0f, 0.0001f)).isTrue()
    }

    @Test
    fun `nearlyEqual should return true for nearly equal floats`() {
        assertThat(CommonUtils.nearlyEqual(1.0f, 1.00001f, 0.001f)).isTrue()
    }

    @Test
    fun `nearlyEqual should return false for significantly different floats`() {
        assertThat(CommonUtils.nearlyEqual(1.0f, 2.0f, 0.0001f)).isFalse()
    }

    @Test
    fun `nearlyEqual should handle zero values`() {
        assertThat(CommonUtils.nearlyEqual(0f, 0f, 0.0001f)).isTrue()
        assertThat(CommonUtils.nearlyEqual(0f, 0.00001f, 0.001f)).isTrue()
    }

    @Test
    fun `nearlyEqual should handle small values near zero`() {
        assertThat(CommonUtils.nearlyEqual(0.0001f, 0.0002f, 0.1f)).isTrue()
    }

    @Test
    fun `dispose should dispose single disposable`() {
        val disposable = mock(Disposable::class.java)
        `when`(disposable.isDisposed).thenReturn(false)

        CommonUtils.dispose(disposable)

        verify(disposable).dispose()
    }

    @Test
    fun `dispose should handle already disposed disposable`() {
        val disposable = mock(Disposable::class.java)
        `when`(disposable.isDisposed).thenReturn(true)

        // Should not throw exception
        CommonUtils.dispose(disposable)
    }

    @Test
    fun `dispose should handle null disposables`() {
        // Should not throw exception
        CommonUtils.dispose(null, null)
    }

    @Test
    fun `dispose should handle multiple disposables`() {
        val disposable1 = mock(Disposable::class.java)
        val disposable2 = mock(Disposable::class.java)
        `when`(disposable1.isDisposed).thenReturn(false)
        `when`(disposable2.isDisposed).thenReturn(false)

        CommonUtils.dispose(disposable1, disposable2)

        verify(disposable1).dispose()
        verify(disposable2).dispose()
    }

    @Test
    fun `dispose should handle mix of null and non-null disposables`() {
        val disposable = mock(Disposable::class.java)
        `when`(disposable.isDisposed).thenReturn(false)

        CommonUtils.dispose(null, disposable, null)

        verify(disposable).dispose()
    }

    @Test
    fun `clone should create deep copy`() {
        data class TestData(var value: String)

        val original = TestData("test")
        val cloned = CommonUtils.clone(original)

        assertThat(cloned).isNotSameInstanceAs(original)
        assertThat(cloned.value).isEqualTo(original.value)

        // Modify original
        original.value = "modified"

        // Clone should be unchanged
        assertThat(cloned.value).isEqualTo("test")
    }
}
