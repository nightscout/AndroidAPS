package app.aaps.pump.insight.descriptors

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Covers [AlertType.Companion]: fromId / fromIncId lookups (hit + miss). */
class AlertTypeTest {

    @Test
    fun fromId_findsKnownAndNullForUnknown() {
        assertThat(AlertType.fromId(31)).isEqualTo(AlertType.REMINDER_01)
        assertThat(AlertType.fromId(7567)).isEqualTo(AlertType.ERROR_13)
        assertThat(AlertType.fromId(-1)).isNull()
    }

    @Test
    fun fromIncId_findsKnownAndNullForUnknown() {
        assertThat(AlertType.fromIncId(1)).isEqualTo(AlertType.REMINDER_01)
        assertThat(AlertType.fromIncId(13)).isEqualTo(AlertType.ERROR_13)
        assertThat(AlertType.fromIncId(999)).isNull()
    }
}
