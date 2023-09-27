package app.aaps.core.ui.elements

import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.SpinnerAdapter
import kotlin.math.max

/**
 * Spinner Helper class that works around some common issues
 * with the stock Android Spinner
 *
 * A Spinner will normally call it's OnItemSelectedListener
 * when you use setSelection(...) in your initialization code.
 * This is usually unwanted behavior, and a common work-around
 * is to use spinner.post(...) with a Runnable to assign the
 * OnItemSelectedListener after layout.
 *
 * If you do not call setSelection(...) manually, the callback
 * may be called with the first item in the adapter you have
 * set. The common work-around for that is to count callbacks.
 *
 * While these workarounds usually *seem* to work, the callback
 * may still be called repeatedly for other reasons while the
 * selection hasn't actually changed. This will happen for
 * example, if the user has accessibility options enabled -
 * which is more common than you might think as several apps
 * use this for different purposes, like detecting which
 * notifications are active.
 *
 * Ideally, your OnItemSelectedListener callback should be
 * coded defensively so that no problem would occur even
 * if the callback was called repeatedly with the same values
 * without any user interaction, so no workarounds are needed.
 *
 * This class does that for you. It keeps track of the values
 * you have set with the setSelection(...) methods, and
 * proxies the OnItemSelectedListener callback so your callback
 * only gets called if the selected item's position differs
 * from the one you have set by code, or the first item if you
 * did not set it.
 *
 * This also means that if the user actually clicks the item
 * that was previously selected by code (or the first item
 * if you didn't set a selection by code), the callback will
 * not fire.
 *
 * To implement, replace current occurrences of:
 *
 * Spinner spinner =
 * (Spinner)findViewById(R.id.xxx);
 *
 * with:
 *
 * SpinnerHelper spinner =
 * new SpinnerHelper(findViewById(R.id.xxx))
 *
 * SpinnerHelper proxies the (my) most used calls to Spinner
 * but not all of them. Should a method not be available, use:
 *
 * spinner.getSpinner().someMethod(...)
 *
 * Or just add the proxy method yourself :)
 *
 * (Quickly) Tested on devices from 2.3.6 through 4.2.2
 *
 * @author Jorrit "Chainfire" Jongma
 * @license WTFPL (do whatever you want with this, nobody cares)
 */
@Suppress("unused")
class SpinnerHelper(val spinner: Spinner) : AdapterView.OnItemSelectedListener {

    private var userTouched = false
    private var lastPosition = -1
    private var proxiedItemSelectedListener: AdapterView.OnItemSelectedListener? = null
    fun setSelection(position: Int) {
        lastPosition = max(-1, position)
        spinner.setSelection(position)
    }

    fun setSelection(position: Int, animate: Boolean) {
        lastPosition = max(-1, position)
        spinner.setSelection(position, animate)
    }

    fun setOnItemSelectedListener(listener: AdapterView.OnItemSelectedListener?) {
        proxiedItemSelectedListener = listener
        setTouchListener()
        spinner.onItemSelectedListener = if (listener == null) null else this
    }

    private fun setTouchListener() {
        spinner.setOnTouchListener { v: View, _: MotionEvent? ->
            v.performClick()
            userTouched = true
            false
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        if (position != lastPosition && userTouched) {
            lastPosition = position
            proxiedItemSelectedListener?.onItemSelected(parent, view, position, id)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        if (lastPosition != -1) {
            lastPosition = -1
            proxiedItemSelectedListener?.onNothingSelected(parent)
        }
    }

    var adapter: SpinnerAdapter
        get() = spinner.adapter
        set(adapter) {
            if (adapter.count > 0) {
                lastPosition = 0
            }
            spinner.adapter = adapter
        }

    val count: Int
        get() = spinner.count

    fun getItemAtPosition(position: Int): Any = spinner.getItemAtPosition(position)

    fun getItemIdAtPosition(position: Int): Long = spinner.getItemIdAtPosition(position)

    val selectedItem: Any
        get() = try {
            spinner.selectedItem
        } catch (e: IndexOutOfBoundsException) {
            adapter.getItem(adapter.count - 1)
        }
    val selectedItemId: Long
        get() = spinner.selectedItemId
    val selectedItemPosition: Int
        get() = spinner.selectedItemPosition
    var isEnabled: Boolean
        get() = spinner.isEnabled
        set(enabled) {
            spinner.isEnabled = enabled
        }
}