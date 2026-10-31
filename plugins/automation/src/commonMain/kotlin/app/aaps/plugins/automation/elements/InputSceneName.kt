package app.aaps.plugins.automation.elements

/**
 * Holds the id of a scene selected by the user. The display name is resolved
 * from [SceneAutomationApi] at edit time and at action runtime; storing the id
 * keeps the reference stable across renames.
 */
class InputSceneName(val id: String = "") {

    var value: String = id
}
