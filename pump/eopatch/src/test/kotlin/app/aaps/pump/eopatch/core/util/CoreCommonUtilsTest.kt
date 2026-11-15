package app.aaps.pump.eopatch.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CoreCommonUtilsTest {

    @Test
    fun `nearlyEqual should return true for identical values`() {
        assertThat(CommonUtils.nearlyEqual(1.0f, 1.0f, 0.0001f)).isTrue()
    }

    @Test
    fun `nearlyEqual should return true for values within epsilon`() {
        assertThat(CommonUtils.nearlyEqual(1.0f, 1.00001f, 0.001f)).isTrue()
    }

    @Test
    fun `nearlyEqual should return false for values outside epsilon`() {
        assertThat(CommonUtils.nearlyEqual(1.0f, 2.0f, 0.0001f)).isFalse()
    }

    @Test
    fun `nearlyEqual should handle zeros`() {
        assertThat(CommonUtils.nearlyEqual(0f, 0f, 0.0001f)).isTrue()
    }

    @Test
    fun `nearlyEqual should handle infinities`() {
        assertThat(CommonUtils.nearlyEqual(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 0.001f)).isTrue()
        assertThat(CommonUtils.nearlyEqual(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.001f)).isFalse()
    }

    @Test
    fun `nearlyEqual should handle subnormal numbers`() {
        assertThat(CommonUtils.nearlyEqual(Float.MIN_VALUE, Float.MIN_VALUE, 0.001f)).isTrue()
    }
}
