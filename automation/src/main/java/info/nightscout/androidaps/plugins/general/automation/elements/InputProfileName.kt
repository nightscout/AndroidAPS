package info.nightscout.androidaps.plugins.general.automation.elements

import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper

class InputProfileName(private val rh: ResourceHelper, private val activePlugin: ActivePlugin, val name: String = "") : Element() {

    var value: String = name

    override fun addToLayout(root: LinearLayout) {
        val profileStore = activePlugin.activeProfileSource.profile ?: return
        val profileList = profileStore.getProfileList()

        root.addView(
            Spinner(root.context).apply {
                adapter = ArrayAdapter(root.context, R.layout.spinner_centered, profileList).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, rh.dpToPx(4), 0, rh.dpToPx(4))
                }
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        value = profileList[position].toString()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                for (i in 0 until profileList.size) if (profileList[i] == value) setSelection(i)
                gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}