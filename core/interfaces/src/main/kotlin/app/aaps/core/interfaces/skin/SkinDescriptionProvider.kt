package app.aaps.core.interfaces.skin

/**
 * Provides skin description information for UI selection.
 * This minimal interface exposes only what's needed for the skin preference dropdown,
 * without exposing View-related skin functionality.
 *
 * Extended by SkinProvider in plugins:main which adds View-related methods.
 */
interface SkinDescriptionProvider {

    /**
     * List of available skins with their class names and description resource IDs.
     * Used for populating the skin selection preference.
     *
     * @return List of pairs: (className, descriptionResId) where descriptionResId is a @StringRes
     */
    val skinDescriptions: List<Pair<String, Int>>
}
