package app.aaps.plugins.main.skins

import app.aaps.core.interfaces.skin.SkinDescriptionProvider

/**
 * Full skin provider interface with View-related functionality.
 * Extends SkinDescriptionProvider to include methods that return SkinInterface.
 *
 * Use this interface for consumers that need activeSkin() or list.
 * Use SkinDescriptionProvider for consumers that only need skinDescriptions.
 */
interface SkinProvider : SkinDescriptionProvider {

    /**
     * Returns the currently active skin based on user preference.
     */
    fun activeSkin(): SkinInterface

    /**
     * List of all available skins, sorted by priority.
     */
    val list: List<SkinInterface>
}
