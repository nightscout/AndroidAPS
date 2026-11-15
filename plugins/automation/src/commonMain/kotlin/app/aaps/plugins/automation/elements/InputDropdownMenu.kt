package app.aaps.plugins.automation.elements

/**
 * The value picked in a dropdown.
 *
 * The list of choices belongs to the Compose editor that draws the dropdown, not here - see
 * `TriggerBTDeviceEditor` and `TriggerStepsCountEditor`. This class only carries the chosen value.
 */
class InputDropdownMenu(var value: String = "") {

    fun setValue(name: String): InputDropdownMenu {
        value = name
        return this
    }
}
