package app.aaps.plugins.configuration.setupwizard.elements

import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import app.aaps.core.ui.elements.NumberPicker
import java.text.DecimalFormat
import javax.inject.Inject

class SWEditNumberWithUnits @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck, private val profileUtil: ProfileUtil) :
    SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private val validator: (Double) -> Boolean =
        if (profileUtil.units == GlucoseUnit.MGDL) { value -> value in (preference as UnitDoublePreferenceKey).minMgdl.toDouble()..(preference as UnitDoublePreferenceKey).maxMgdl.toDouble() }
        else { value -> value in (preference as UnitDoublePreferenceKey).minMgdl * Constants.MGDL_TO_MMOLL..(preference as UnitDoublePreferenceKey).maxMgdl * Constants.MGDL_TO_MMOLL }
    private var updateDelay = 0

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val watcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (validator.invoke(SafeParse.stringToDouble(s.toString())))
                    save(s.toString(), updateDelay.toLong())
            }

            override fun afterTextChanged(s: Editable) {}
        }

        val l = TextView(context)
        l.id = View.generateViewId()
        label?.let { l.setText(it) }
        l.setTypeface(l.typeface, Typeface.BOLD)
        layout.addView(l)
        var initValue = preferences.get(preference as UnitDoublePreferenceKey)
        initValue = profileUtil.valueInCurrentUnitsDetect(initValue)
        val numberPicker = NumberPicker(context)
        if (profileUtil.units == GlucoseUnit.MMOL)
            numberPicker.setParams(initValue, (preference as UnitDoublePreferenceKey).minMgdl * Constants.MGDL_TO_MMOLL, (preference as UnitDoublePreferenceKey).maxMgdl * Constants.MGDL_TO_MMOLL, 0.1, DecimalFormat("0.0"), false, null, watcher)
        else
            numberPicker.setParams(initValue, (preference as UnitDoublePreferenceKey).minMgdl.toDouble(), (preference as UnitDoublePreferenceKey).maxMgdl.toDouble(), 1.0, DecimalFormat("0"), false, null, watcher)

        layout.addView(numberPicker)
        val c = TextView(context)
        c.id = View.generateViewId()
        comment?.let { c.setText(it) }
        c.setTypeface(c.typeface, Typeface.ITALIC)
        layout.addView(c)
        super.generateDialog(layout)
    }

    fun preference(preference: UnitDoublePreferenceKey): SWEditNumberWithUnits {
        this.preference = preference
        return this
    }

    fun updateDelay(updateDelay: Int): SWEditNumberWithUnits {
        this.updateDelay = updateDelay
        return this
    }

}