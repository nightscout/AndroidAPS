package app.aaps.plugins.configuration.maintenance.cloud.events

import app.aaps.core.interfaces.rx.events.Event

/**
 * Event fired when cloud storage connection status changes.
 * This allows UI components to update immediately when connection errors occur.
 */
class EventCloudStorageStatusChanged : Event()
