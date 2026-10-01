package app.aaps.core.interfaces.pump.actions

/**
 * Identifies a [CustomAction] when it is dispatched back to its driver.
 *
 * Retained deliberately along with [CustomAction] - see the note there before removing it as dead
 * code. Its only implementation, `MedtronicCustomActionType`, is likewise unreferenced today.
 */
interface CustomActionType {

    fun getKey(): String
}