package app.aaps.ui.widget.glance

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.injectMetroMembers
import dev.zacsweers.metro.Inject

/**
 * The app-graph dependencies a Glance widget needs, fetched on demand.
 *
 * Glance instantiates [androidx.glance.appwidget.GlanceAppWidget] in code paths that do not go through
 * an injected receiver - notably `GlanceAppWidgetManager.addAllReceiversAndProvidersToPreferences`,
 * which reflectively constructs every registered receiver to read provider info. If the widget's
 * constructor needed `lateinit` deps, that path crashes with `UninitializedPropertyAccessException`.
 *
 * Each [androidx.glance.appwidget.GlanceAppWidget] should therefore have a no-arg constructor and call
 * [from] inside `provideGlance(context, id)`.
 *
 * The entry needed for [from] to work lives in [app.aaps.ui.di.UiMemberInjectors]; without it
 * `injectMetroMembers` fails loudly rather than leaving the fields unset.
 */
class WidgetDependencies {

    @Inject lateinit var widgetStateLoader: WidgetStateLoader
    @Inject lateinit var bgGraphStateLoader: BgGraphStateLoader
    @Inject lateinit var config: Config

    companion object {

        fun from(context: Context): WidgetDependencies =
            WidgetDependencies().also { context.injectMetroMembers(it) }
    }
}
