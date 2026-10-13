package app.aaps.core.interfaces.rx.events

import app.aaps.core.interfaces.resources.ResourceHelper

/**
 * Fired when a preference value changes.
 */
class EventPreferenceChange : Event {

    /**
     * The key of the preference that changed.
     */
    var changedKey: String? = null
        private set

    /**
     * Creates a new instance with the given preference key.
     *
     * @param key The key of the preference that changed.
     */
    constructor(key: String) {
        changedKey = key
    }

    /**
     * Creates a new instance with the given preference key resource ID.
     *
     * @param key The resource ID of the key of the preference that changed.
     * @param rh The [ResourceHelper] to resolve the key string.
     */
    constructor(key: Int, rh: ResourceHelper) {
        changedKey = rh.gs(key)
    }

    /**
     * Checks if the changed preference has the given key.
     *
     * @param key The key to check.
     * @return True if the changed preference has the given key.
     */
    fun isChanged(key: String): Boolean {
        return changedKey == key
    }

    /**
     * Checks if the changed preference has the given key resource ID.
     *
     * @param key The resource ID of the key to check.
     * @param rh The [ResourceHelper] to resolve the key string.
     * @return True if the changed preference has the given key.
     */
    fun isChanged(key: Int, rh: ResourceHelper): Boolean {
        return changedKey == rh.gs(key)
    }
}