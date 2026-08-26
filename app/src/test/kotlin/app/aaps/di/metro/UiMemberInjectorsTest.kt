package app.aaps.di.metro

import app.aaps.ui.activities.ErrorActivity
import app.aaps.ui.widget.BgGraphWidget
import app.aaps.ui.widget.CompactBgWidget
import app.aaps.ui.widget.SmallWidget
import app.aaps.ui.widget.Widget
import app.aaps.ui.widget.WidgetConfigureActivity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The `:ui` entry points Android constructs must have a member injector entry.
 *
 * Same reason as [ReceiverInjectorsTest]: a missing entry is not a build error. Android builds the
 * class, the lookup by runtime class finds nothing, and `injectMetroMembers` throws - for a widget that
 * is when the home screen tries to draw it, so the user sees a widget that will not load.
 *
 * The four widgets are `AppWidgetProvider`s. They cannot extend `MetroBroadcastReceiver`, because they
 * already extend a framework base, so they call `injectMetroMembers` themselves in `onReceive`. That
 * makes the entry easier to forget than for a class whose base class does it.
 */
class UiMemberInjectorsTest {

    private val injectors get() = testRoot().contributedMemberInjectors

    @Test
    fun `the four home screen widgets have injectors`() {
        assertThat(injectors.keys).containsAtLeast(
            Widget::class,
            BgGraphWidget::class,
            CompactBgWidget::class,
            SmallWidget::class
        )
    }

    @Test
    fun `the ui activities have injectors`() {
        assertThat(injectors.keys).containsAtLeast(ErrorActivity::class, WidgetConfigureActivity::class)
    }
}
