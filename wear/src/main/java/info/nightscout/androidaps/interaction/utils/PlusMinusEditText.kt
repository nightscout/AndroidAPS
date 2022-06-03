package info.nightscout.androidaps.interaction.utils

import android.os.Handler
import kotlin.jvm.JvmOverloads
import android.view.View.OnTouchListener
import android.view.View.OnGenericMotionListener
import android.widget.TextView
import android.view.MotionEvent
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import java.text.NumberFormat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Created by mike on 28.06.2016.
 */
class PlusMinusEditText @JvmOverloads constructor(
    view: View,
    editTextID: Int,
    plusID: Int,
    minusID: Int,
    initValue: Double,
    minValue: Double,
    maxValue: Double,
    step: Double,
    formatter: NumberFormat,
    allowZero: Boolean,
    roundRobin: Boolean = false
) : View.OnKeyListener, OnTouchListener, View.OnClickListener, OnGenericMotionListener {

    var editText: TextView
        private set
    private var minusImage: ImageView
    private var plusImage: ImageView
    private var value: Double
    private var minValue: Double
    private var maxValue: Double
    private var step: Double
    private var formatter: NumberFormat
    private var allowZero: Boolean
    private var roundRobin: Boolean
    private var mChangeCounter = 0
    private var mLastChange: Long = 0
    private val mHandler: Handler
    private var mUpdater: ScheduledExecutorService? = null

    private inner class UpdateCounterTask(private val mInc: Boolean) : Runnable {

        private var repeated = 0
        private var multiplier = 1
        override fun run() {
            val msg = Message()
            val doubleLimit = 5
            if (repeated % doubleLimit == 0) multiplier *= 2
            repeated++
            msg.arg1 = multiplier
            msg.arg2 = repeated
            if (mInc) {
                msg.what = MSG_INC
            } else {
                msg.what = MSG_DEC
            }
            mHandler.sendMessage(msg)
        }
    }

    private fun inc(multiplier: Int) {
        value += step * multiplier
        if (value > maxValue) {
            if (roundRobin) {
                value = minValue
            } else {
                value = maxValue
                stopUpdating()
            }
        }
        updateEditText()
    }

    private fun dec(multiplier: Int) {
        value -= step * multiplier
        if (value < minValue) {
            if (roundRobin) {
                value = maxValue
            } else {
                value = minValue
                stopUpdating()
            }
        }
        updateEditText()
    }

    private fun updateEditText() {
        if (value == 0.0 && !allowZero) editText.text = "" else editText.text = formatter.format(value)
    }

    private fun startUpdating(inc: Boolean) {
        if (mUpdater != null) {
            return
        }

        mUpdater = Executors.newSingleThreadScheduledExecutor()
        mUpdater?.scheduleAtFixedRate(
            UpdateCounterTask(inc), 200, 200,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopUpdating() {
        mUpdater?.shutdownNow()
        mUpdater = null
    }

    override fun onClick(v: View) {
        if (mUpdater == null) {
            if (v === plusImage) {
                inc(1)
            } else {
                dec(1)
            }
        }
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        val isKeyOfInterest = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
        val isReleased = event.action == KeyEvent.ACTION_UP
        val isPressed = event.action == KeyEvent.ACTION_DOWN
        if (isKeyOfInterest && isReleased) {
            stopUpdating()
        } else if (isKeyOfInterest && isPressed) {
            startUpdating(v === plusImage)
        }
        return false
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val isReleased = event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL
        val isPressed = event.action == MotionEvent.ACTION_DOWN
        if (isReleased) {
            stopUpdating()
        } else if (isPressed) {
            startUpdating(v === plusImage)
        }
        return false
    }

    override fun onGenericMotion(v: View, ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            val now = System.currentTimeMillis()
            if (now - mLastChange > THRESHOLD_TIME) mChangeCounter = 0
            val dynamicMultiplier = if (mChangeCounter < THRESHOLD_COUNTER) 1 else if (mChangeCounter < THRESHOLD_COUNTER_LONG) 2 else 4
            val delta = -ev.getAxisValue(MotionEventCompat.AXIS_SCROLL)
            if (delta > 0) {
                inc(dynamicMultiplier)
            } else {
                dec(dynamicMultiplier)
            }
            mLastChange = System.currentTimeMillis()
            mChangeCounter++
            return true
        }
        return false
    }

    companion object {

        private const val THRESHOLD_COUNTER = 5
        private const val THRESHOLD_COUNTER_LONG = 10
        private const val THRESHOLD_TIME = 100
        private const val MSG_INC = 0
        private const val MSG_DEC = 1
    }

    init {
        editText = view.findViewById(editTextID)
        minusImage = view.findViewById(minusID)
        plusImage = view.findViewById(plusID)
        value = initValue
        this.minValue = minValue
        this.maxValue = maxValue
        this.step = step
        this.formatter = formatter
        this.allowZero = allowZero
        this.roundRobin = roundRobin
        mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MSG_INC -> {
                        inc(msg.arg1)
                        return
                    }

                    MSG_DEC -> {
                        dec(msg.arg1)
                        return
                    }
                }
                super.handleMessage(msg)
            }
        }
        minusImage.setOnTouchListener(this)
        minusImage.setOnKeyListener(this)
        minusImage.setOnClickListener(this)
        plusImage.setOnTouchListener(this)
        plusImage.setOnKeyListener(this)
        plusImage.setOnClickListener(this)
        editText.setOnGenericMotionListener(this)
        updateEditText()
    }
}
