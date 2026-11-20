package app.aaps.ui.compose.main

import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.source.CgmAttention
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** When the bottom bar shows the BG source button: the BG quality, and a CGM driver that needs the user, as a pump does. */
class BgSourceShortcutTest {

    @Test fun `good readings and a driver with nothing to fix show no button`() {
        assertThat(showBgSourceShortcut(BgQualityCheck.State.FIVE_MIN_DATA, attention = null, hasScreen = true)).isFalse()
        assertThat(showBgSourceShortcut(BgQualityCheck.State.RECALCULATED, attention = null, hasScreen = true)).isFalse()
    }

    @Test fun `flat or doubled readings show it`() {
        assertThat(showBgSourceShortcut(BgQualityCheck.State.FLAT, attention = null, hasScreen = true)).isTrue()
        assertThat(showBgSourceShortcut(BgQualityCheck.State.DOUBLED, attention = null, hasScreen = true)).isTrue()
    }

    /** Not paired or sign-in failed: the button leads to the driver's screen, which shows the fix. */
    @Test fun `a driver that needs the user shows it, also with good readings`() {
        assertThat(showBgSourceShortcut(BgQualityCheck.State.FIVE_MIN_DATA, CgmAttention.NOT_PAIRED, hasScreen = true)).isTrue()
        assertThat(showBgSourceShortcut(BgQualityCheck.State.UNKNOWN, CgmAttention.SIGN_IN_FAILED, hasScreen = true)).isTrue()
    }

    /** A button that opens nothing is worse than none. */
    @Test fun `a source without a screen never shows it`() {
        assertThat(showBgSourceShortcut(BgQualityCheck.State.FLAT, CgmAttention.NOT_PAIRED, hasScreen = false)).isFalse()
    }
}
