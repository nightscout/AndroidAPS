package info.nightscout.plugins.profile.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import info.nightscout.core.ui.elements.NumberPicker
import info.nightscout.core.ui.elements.SpinnerHelper
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.SafeParse.stringToDouble
import info.nightscout.shared.utils.DateUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.NumberFormat

class TimeListEdit(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val view: View,
    private val resLayoutId: Int,
    private val tagPrefix: String,
    private var label: String,
    private val data1: JSONArray,
    private val data2: JSONArray?,
    range1: DoubleArray,
    range2: DoubleArray?,
    private val step: Double,
    formatter: NumberFormat,
    save: Runnable?
) {

    private val intervals = arrayOfNulls<View>(24)
    private val spinners = arrayOfNulls<SpinnerHelper>(24)
    private val numberPickers1 = arrayOfNulls<NumberPicker>(24)
    private val numberPickers2 = arrayOfNulls<NumberPicker>(24)
    private val addButtons = arrayOfNulls<ImageView>(24)
    private val removeButtons = arrayOfNulls<ImageView>(24)
    private var finalAdd: ImageView? = null
    private val min: Double
    private val max: Double
    private val min2: Double
    private val max2: Double
    private val formatter: NumberFormat
    private val save: Runnable?
    private var layout: LinearLayout? = null
    private var textLabel: TextView? = null
    private var inflatedUntil = -1

    init {
        min = range1[0]
        max = range1[1]
        min2 = range2?.get(0) ?: 0.0
        max2 = range2?.get(1) ?: 0.0
        this.formatter = formatter
        this.save = save
        buildView()
    }

    private fun buildView() {
        val layout = view.findViewById<LinearLayout>(resLayoutId).also {
            this.layout = it
            it.removeAllViewsInLayout()
        } ?: return
        TextView(context).also {
            this.textLabel = it
            it.text = label
            it.gravity = Gravity.CENTER
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { llp ->
                llp.setMargins(0, 5, 0, 5)
            }
            TextViewCompat.setTextAppearance(it, android.R.style.TextAppearance_Medium)
            layout.addView(it)
        }
        var i = 0
        while (i < 24 && i < itemsCount()) {
            inflateRow(i)
            inflatedUntil = i
            i++
        }

        // last "plus" to append new interval
        val factor = layout.context.resources.displayMetrics.density
        ImageView(context).also {
            this.finalAdd = it
            it.setImageResource(info.nightscout.core.main.R.drawable.ic_add)
            it.contentDescription = layout.context.resources.getString(info.nightscout.plugins.R.string.a11y_add_new_to_list)
            layout.addView(it)
            it.layoutParams = LinearLayout.LayoutParams((35.0 * factor).toInt(), (35 * factor).toInt()).also { llp ->
                llp.setMargins(0, 25, 0, 25) // llp.setMargins(left, top, right, bottom);
                llp.gravity = Gravity.CENTER
            }
            it.setOnClickListener {
                addItem(itemsCount(), if (itemsCount() > 0) secondFromMidnight(itemsCount() - 1) + ONE_HOUR_IN_SECONDS else 0)
                callSave()
                log()
                fillView()
            }
        }
        fillView()
    }

    private fun inflateRow(position: Int) {
        val resource =
            if (data2 == null) info.nightscout.plugins.R.layout.timelistedit_element
            else info.nightscout.plugins.R.layout.timelistedit_element_vertical
        val childView = LayoutInflater.from(context).inflate(resource, layout, false).also {
            intervals[position] = it
            layout?.addView(it)
        }
        childView.findViewById<ImageView>(info.nightscout.plugins.R.id.timelistedit_add).also {
            addButtons[position] = it
            it.setOnClickListener {
                val seconds = secondFromMidnight(position)
                addItem(position, seconds)
                // for here for the rest of values
                for (i in position + 1 until itemsCount()) {
                    if (secondFromMidnight(i - 1) >= secondFromMidnight(i)) {
                        editItem(i, secondFromMidnight(i - 1) + ONE_HOUR_IN_SECONDS, value1(i), value2(i))
                    }
                }
                while (itemsCount() > 24 || secondFromMidnight(itemsCount() - 1) > 23 * ONE_HOUR_IN_SECONDS) removeItem(itemsCount() - 1)
                callSave()
                log()
                fillView()
            }
        }
        childView.findViewById<ImageView>(info.nightscout.plugins.R.id.timelistedit_remove).also {
            removeButtons[position] = it
            it.setOnClickListener {
                removeItem(position)
                callSave()
                log()
                fillView()
            }
        }
        SpinnerHelper(childView.findViewById(info.nightscout.plugins.R.id.timelistedit_time)).also {
            spinners[position] = it
            it.setOnItemSelectedListener(
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View, selected: Int, id: Long) {
                        val seconds = (it.adapter as SpinnerAdapter).valueForPosition(selected)
                        editItem(position, seconds, value1(position), value2(position))
                        log()
                        callSave()
                        fillView()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            )
        }
        childView.findViewById<NumberPicker>(info.nightscout.plugins.R.id.timelistedit_edit1).also {
            numberPickers1[position] = it
            it.setTextWatcher(object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    val value1 = stringToDouble(it.text, 0.0)
                    var value2 = value2(position)
                    if (data2 != null && value1 > value2) {
                        value2 = value1
                        numberPickers2[position]?.value = value2
                    }
                    editItem(position, secondFromMidnight(position), value1, value2)
                    callSave()
                    log()
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            })
            it.tag = "$tagPrefix-1-$position"
        }
        childView.findViewById<NumberPicker>(info.nightscout.plugins.R.id.timelistedit_edit2).also {
            numberPickers2[position] = it
            it.setTextWatcher(object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    var value1 = value1(position)
                    val value2 = stringToDouble(it.text, 0.0)
                    if (data2 != null && value2 < value1) {
                        value1 = value2
                        numberPickers1[position]?.value = value1
                    }
                    editItem(position, secondFromMidnight(position), value1, value2)
                    callSave()
                    log()
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            })
            it.tag = "$tagPrefix-2-$position"
        }
    }

    private fun fillView() {
        for (i in 0..23) {
            if (i < itemsCount()) {
                intervals[i]?.visibility = View.VISIBLE
                buildInterval(i)
            } else if (i <= inflatedUntil) {
                intervals[i]?.visibility = View.GONE
            }
        }
        finalAdd?.visibility =
            if (!(itemsCount() > 0 && secondFromMidnight(itemsCount() - 1) == 23 * ONE_HOUR_IN_SECONDS)) View.VISIBLE
            else View.GONE
    }

    private fun buildInterval(i: Int) {
        val timeSpinner = spinners[i] ?: return
        val editText1 = numberPickers1[i] ?: return
        val editText2 = numberPickers2[i] ?: return
        val previous = if (i == 0) -1 * ONE_HOUR_IN_SECONDS else secondFromMidnight(i - 1)
        var next = if (i == itemsCount() - 1) 24 * ONE_HOUR_IN_SECONDS else secondFromMidnight(i + 1)
        if (i == 0) next = ONE_HOUR_IN_SECONDS
        fillSpinner(timeSpinner, secondFromMidnight(i), previous, next)
        editText1.setParams(value1(i), min, max, step, formatter, false, null)
        editText2.setParams(value2(i), min2, max2, step, formatter, false, null)
        if (data2 == null) {
            editText2.visibility = View.GONE
        }
        removeButtons[i]?.visibility =
            if (itemsCount() == 1 || i == 0) View.INVISIBLE
            else View.VISIBLE
        addButtons[i]?.visibility =
            if (itemsCount() >= 24 || secondFromMidnight(i) >= 82800) View.INVISIBLE
            else View.VISIBLE
    }

    internal class SpinnerAdapter(context: Context, resource: Int, objects: List<CharSequence>, var values: List<Int>) : ArrayAdapter<CharSequence?>(context, resource, objects) {

        fun valueForPosition(position: Int): Int = values[position]
    }

    private fun fillSpinner(spinner: SpinnerHelper, secondsFromMidnight: Int, previous: Int, next: Int) {
        var posInList = 0
        val timeList = ArrayList<CharSequence>()
        val timeListValues = ArrayList<Int>()
        var pos = 0
        var t = previous + ONE_HOUR_IN_SECONDS
        while (t < next) {
            timeList.add(dateUtil.timeStringFromSeconds(t))
            timeListValues.add(t)
            if (secondsFromMidnight == t) posInList = pos
            pos++
            t += ONE_HOUR_IN_SECONDS
        }
        val adapter = SpinnerAdapter(
            context,
            info.nightscout.core.ui.R.layout.spinner_centered, timeList, timeListValues
        )
        spinner.adapter = adapter
        spinner.setSelection(posInList, false)
        adapter.notifyDataSetChanged()
    }

    private fun itemsCount(): Int {
        return data1.length()
    }

    private fun secondFromMidnight(index: Int): Int {
        try {
            val item = data1[index] as JSONObject
            if (item.has("timeAsSeconds")) {
                var time = item.getInt("timeAsSeconds")
                if (index == 0 && time != 0) {
                    // fix the bug, every array must start with 0
                    item.put("timeAsSeconds", 0)
                    time = 0
                }
                return time
            }
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return 0
    }

    private fun value1(index: Int): Double {
        try {
            val item = data1[index] as JSONObject
            if (item.has("value")) {
                return item.getDouble("value")
            }
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return 0.0
    }

    private fun value2(index: Int): Double {
        if (data2 != null) {
            try {
                val item = data2[index] as JSONObject
                if (item.has("value")) {
                    return item.getDouble("value")
                }
            } catch (e: JSONException) {
                aapsLogger.error("Unhandled exception", e)
            }
        }
        return 0.0
    }

    private fun editItem(index: Int, timeAsSeconds: Int, value1: Double, value2: Double) {
        try {
            val time: String
            val hour = timeAsSeconds / 60 / 60
            val df = DecimalFormat("00")
            time = df.format(hour.toLong()) + ":00"
            val newObject1 = JSONObject()
            newObject1.put("time", time)
            newObject1.put("timeAsSeconds", timeAsSeconds)
            newObject1.put("value", value1)
            data1.put(index, newObject1)
            if (data2 != null) {
                val newObject2 = JSONObject()
                newObject2.put("time", time)
                newObject2.put("timeAsSeconds", timeAsSeconds)
                newObject2.put("value", value2)
                data2.put(index, newObject2)
            }
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    private fun addItem(index: Int, timeAsSeconds: Int) {
        if (itemsCount() >= 24) return
        if (itemsCount() > inflatedUntil) {
            layout?.removeView(finalAdd)
            inflateRow(++inflatedUntil)
            layout?.addView(finalAdd)
        }
        try {
            // shift data
            for (i in data1.length() downTo index + 1) {
                data1.put(i, data1[i - 1])
                data2?.put(i, data2[i - 1])
            }
            // add new object
            editItem(index, timeAsSeconds, 0.0, 0.0)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    private fun removeItem(index: Int) {
        data1.remove(index)
        data2?.remove(index)
    }

    private fun log() {
        for (i in 0 until data1.length()) {
            aapsLogger.debug(i.toString() + ": @" + dateUtil.timeStringFromSeconds(secondFromMidnight(i)) + " " + value1(i) + if (data2 != null) " " + value2(i) else "")
        }
    }

    private fun callSave() {
        save?.run()
    }

    fun updateLabel(txt: String) {
        label = txt
        textLabel?.text = txt
    }

    companion object {

        private const val ONE_HOUR_IN_SECONDS = 60 * 60
    }
}