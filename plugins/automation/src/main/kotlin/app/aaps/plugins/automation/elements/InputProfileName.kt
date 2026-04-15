package app.aaps.plugins.automation.elements

import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.resources.ResourceHelper

class InputProfileName(private val rh: ResourceHelper, private val localProfileManager: LocalProfileManager, val name: String = "", private val addActive: Boolean = false) {

    var value: String = name
}
