package info.nightscout.androidaps.plugins.general.automation.elements

import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*

class InputDropdownMenu(private val resourceHelper: ResourceHelper) : Element() {

    private var itemList: ArrayList<CharSequence> = ArrayList()
    var value: String = ""

    constructor(resourceHelper: ResourceHelper, name: String) : this(resourceHelper) {
        value = name
    }

    @Suppress("unused")
    constructor(resourceHelper: ResourceHelper, another: InputDropdownMenu) : this(resourceHelper) {
        value = another.value
    }

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            Spinner(root.context).apply {
                adapter = ArrayAdapter(root.context, R.layout.spinner_centered, itemList).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                    it.setMargins(0, resourceHelper.dpToPx(4), 0, resourceHelper.dpToPx(4))
                }

                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        setValue(itemList[position].toString())
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                gravity = Gravity.CENTER_HORIZONTAL
                for (i in 0 until itemList.size) if (itemList[i] == value) setSelection(i)
            })
    }

    fun setValue(name: String): InputDropdownMenu {
        value = name
        return this
    }

    fun setList(values: ArrayList<CharSequence>) {
        itemList = ArrayList(values)
    }

    // For testing only
    fun add(item: String) {
        itemList.add(item)
    }
}