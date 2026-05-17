package app.aaps.ui.widget.glance

import app.aaps.core.interfaces.configuration.Config
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for accessing app-graph dependencies from Glance widgets.
 *
 * Glance instantiates [androidx.glance.appwidget.GlanceAppWidget] in code paths
 * that don't go through a Hilt-injected receiver — notably
 * `GlanceAppWidgetManager.addAllReceiversAndProvidersToPreferences`, which
 * reflectively constructs every registered receiver to read provider info.
 * If the widget's constructor needed `lateinit` deps, that path crashes with
 * `UninitializedPropertyAccessException`.
 *
 * Each [androidx.glance.appwidget.GlanceAppWidget] should therefore have a
 * no-arg constructor and resolve its deps inside `provideGlance(context, id)`
 * via `EntryPointAccessors.fromApplication(context.applicationContext, WidgetDependencies::class.java)`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDependencies {

    fun widgetStateLoader(): WidgetStateLoader
    fun bgGraphStateLoader(): BgGraphStateLoader
    fun config(): Config
}
