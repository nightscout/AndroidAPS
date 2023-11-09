package app.aaps.plugins.automation.elements

import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper

class InputProfileName(private val rh: ResourceHelper, private val activePlugin: ActivePlugin, val name: String = "", private val addActive: Boolean = false) : Element {

    var value: String = name

    override fun addToLayout(root: LinearLayout) {
        val profileStore = activePlugin.activeProfileSource.profile ?: return
        val profileList = profileStore.getProfileList()
        if (addActive)
            profileList.add(0, rh.gs(app.aaps.core.ui.R.string.active))
        root.addView(
            Spinner(root.context).apply {
                adapter = ArrayAdapter(root.context, app.aaps.core.ui.R.layout.spinner_centered, profileList).apply {
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