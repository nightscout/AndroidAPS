package app.aaps.pump.equil.compose

/**
 * Steps for Equil pump activation/deactivation/change-insulin wizards.
 */
enum class EquilWizardStep {

    // PAIR flow: Assemble → SerialNumber → Fill → Attach → Air → Confirm
    ASSEMBLE,
    SERIAL_NUMBER,
    FILL,
    ATTACH,
    AIR,
    CONFIRM,

    // CHANGE_INSULIN flow: ChangeInsulin → Assemble → Fill → Attach → Air → Confirm
    CHANGE_INSULIN,

    // UNPAIR flow: UnpairDetach → UnpairConfirm
    UNPAIR_DETACH,
    UNPAIR_CONFIRM,

    // Terminal
    CANCEL
}

enum class EquilWorkflow(val totalSteps: Int) {
    PAIR(6),
    CHANGE_INSULIN(6),
    UNPAIR(2)
}
