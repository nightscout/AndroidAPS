package info.nightscout.androidaps.plugins.general.automation.elements

import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class InputProfileName(injector: HasAndroidInjector) : Element(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider

    var value: String = ""

    constructor(injector: HasAndroidInjector, name: String) : this(injector) {
        value = name
    }

    override fun addToLayout(root: LinearLayout) {
        val profileStore = activePlugin.activeProfileInterface.profile ?: return
        val profileList = profileStore.getProfileList()
        val adapter = ArrayAdapter(root.context, R.layout.spinner_centered, profileList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val spinner = Spinner(root.context)
        spinner.adapter = adapter
        val spinnerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        spinnerParams.setMargins(0, resourceHelper.dpToPx(4), 0, resourceHelper.dpToPx(4))
        spinner.layoutParams = spinnerParams
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                value = profileList[position].toString()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinner.setSelection(0)
        val l = LinearLayout(root.context)
        l.orientation = LinearLayout.VERTICAL
        l.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        l.addView(spinner)
        root.addView(l)
    }
}