package app.aaps.core.interfaces.configuration

import app.aaps.core.keys.interfaces.NonPreferenceKey

/**
 * Exposes the union of preference keys whose changes should trigger a republish of
 * the running configuration document. Implemented by `RunningConfigurationImpl`.
 *
 * Subscribers should additionally listen to `EventConfigBuilderChange` to catch
 * plugin switches that are not preference-driven.
 */
interface RunningConfigurationKeys {

    /**
     * "Cold" preference keys — values published in the rarely-changing config doc. The keys whose
     * `SyncSpec.channel` is Cold (plugin selection, plugin settings, scene/quick-wizard/automation/insulin
     * definitions, overview keys). Excludes runtime state (see [hotKeys]).
     */
    fun observableKeys(): List<NonPreferenceKey>

    /**
     * "Hot" preference keys — runtime state that changes frequently (active scene
     * start/stop/expire). Published to a separate, small settings doc so a scene
     * lifecycle event does not re-upload the whole cold config.
     */
    fun hotKeys(): List<NonPreferenceKey>
}
