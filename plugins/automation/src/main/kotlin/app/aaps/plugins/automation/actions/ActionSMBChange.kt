package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.comment
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.utils.lenientBoolean
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDropdownOnOffMenu
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Provider

class ActionSMBChange(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val dateUtil: DateUtil,
    private val preferences: Preferences
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var smbState: InputDropdownOnOffMenu = InputDropdownOnOffMenu(rh, true)

    override fun friendlyName(): Int = R.string.changeSmbState
    override fun shortDescription(): String = rh.gs(R.string.changeSmbTo, smbState.toTextValue())
    override fun composeIcon() = IcSmb
    override fun elementType() = ElementType.INSULIN

    override suspend fun doAction(): PumpEnactResult {
        preferences.put(BooleanKey.ApsUseSmb, smbState.value)
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = buildJsonObject { put("smbState", smbState.value) }
        return buildJsonObject {
            put("type", this@ActionSMBChange.javaClass.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        smbState.value = o.lenientBoolean("smbState", true)
        return this
    }

    override fun isValid(): Boolean = true
}