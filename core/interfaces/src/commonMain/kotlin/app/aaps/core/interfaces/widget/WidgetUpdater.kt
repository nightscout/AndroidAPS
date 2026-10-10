package app.aaps.core.interfaces.widget

/**
 * Fires a refresh of the app widget. Implementation lives in the `ui` module
 * so the UI-only dependencies (Glance, chip composables, color palette) stay
 * out of modules that only care about "tell the widget to redraw".
 */
interface WidgetUpdater {

    /**
     * Triggers an asynchronous refresh of all installed widget instances.
     *
     * @param from free-form tag used in debug logs to identify the caller.
     */
    fun update(from: String)
}
