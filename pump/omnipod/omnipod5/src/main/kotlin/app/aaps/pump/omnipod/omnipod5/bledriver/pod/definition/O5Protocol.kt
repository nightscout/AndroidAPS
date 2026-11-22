package app.aaps.pump.omnipod.omnipod5.bledriver.pod.definition

/**
 * The Omnipod 5 pod protocol uses a fixed nonce for every `NonceEnabledCommand` - same
 * finding as Dash's own fixed `NONCE` constant in
 * [app.aaps.pump.omnipod.dash.driver.OmnipodDashManagerImpl], a pod-firmware-level
 * property of the shared command/response layer, not specific to either pod type.
 * Shared here so [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin] and
 * [app.aaps.pump.omnipod.omnipod5.ui.wizard.compose.O5OmnipodWizardViewModel] don't each
 * define their own copy.
 */
const val O5_FIXED_NONCE = 1229869870
