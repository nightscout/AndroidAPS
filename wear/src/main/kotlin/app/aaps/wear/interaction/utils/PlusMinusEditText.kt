package app.aaps.wear.interaction.utils

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.OnGenericMotionListener
import android.view.View.OnTouchListener
import android.widget.TextView
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Created by mike on 28.06.2016.
 */
class PlusMinusEditText @JvmOverloads constructor(
    private val binding: EditPlusMinusViewAdapter,
    initValue: Double,
    private val minValue: Double,
    private val maxValue: Double,
    private val stepValues: List<Double>,
    private val formatter: NumberFormat,
    private val allowZero: Boolean,
    label: String,
    private val roundRobin: Boolean = false,
) : View.OnKeyListener, OnTouchListener, View.OnClickListener, OnGenericMotionListener {

    constructor(
        binding: EditPlusMinusViewAdapter,
        initValue: Double,
        minValue: Double,
        maxValue: Double,
        step: Double,
        formatter: NumberFormat,
        allowZero: Boolean,
        label: String,
        roundRobin: Boolean = false
    ) : this(binding, initValue, minValue, maxValue, listOf(step), formatter, allowZero, label, roundRobin)

    private val stepGeneral: Double = stepValues[0]
    var editText: TextView
        private set
    private var value: Double
    private var mChangeCounter = 0
    private var mLastChange: Long = 0
    private val handler: Handler
    private var mUpdater: ScheduledExecutorService? = null

    private inner class UpdateCounterTask(private val mInc: Boolean, private val step: Double) : Runnable {

        private var repeated = 0
        private var multiplier = 1
        override fun run() {
            val msg = Message()
            val doubleLimit = 5
            val multipleButtons = mInc && (binding.plusButton2 != null || binding.plusButton3 != null)
            if (!multipleButtons && repeated % doubleLimit == 0) multiplier *= 2
            val bundle = Bundle()
            bundle.putDouble(STEP, step)
            bundle.putInt(MULTIPLIER, multiplier)
            msg.data = bundle

            if (mInc) {
                msg.what = MSG_INC
            } else {
                msg.what = MSG_DEC
            }
            handler.sendMessage(msg)
        }
    }

    private fun inc(multiplier: Int, step: Double, vibrate: Boolean = true) {
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
        if (vibrate) vibrateDevice()
    }

    private fun dec(multiplier: Int, step: Double, vibrate: Boolean = true) {
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
        if (vibrate) vibrateDevice()
    }

    private fun vibrateDevice() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (binding.root.context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            binding.root.context.getSystemService(Vibrator::class.java)
        }

        vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun updateEditText() {
        if (value == 0.0 && !allowZero) editText.text = "" else editText.text = formatter.format(value)
    }

    private fun startUpdating(inc: Boolean, step: Double) {
        if (mUpdater != null) {
            return
        }

        mUpdater = Executors.newSingleThreadScheduledExecutor()
        mUpdater?.scheduleWithFixedDelay(
            UpdateCounterTask(inc, step), 200, 200,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopUpdating() {
        mUpdater?.shutdownNow()
        mUpdater = null
    }

    private fun getStep(v: View): Double {
        return when (v) {
            binding.plusButton1 -> stepValues[0]
            binding.plusButton2 -> stepValues[1]
            binding.plusButton3 -> stepValues[2]
            else                -> stepValues[0]
        }
    }

    private fun isIncrement(v: View): Boolean {
        return when (v) {
            binding.plusButton1 -> true
            binding.plusButton2 -> true
            binding.plusButton3 -> true
            else                -> false
        }
    }

    override fun onClick(v: View) {
        if (mUpdater == null) {
            if (isIncrement(v)) {
                inc(1, getStep(v))
            } else {
                dec(1, getStep(v))
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
            startUpdating(isIncrement(v), stepGeneral)
        }
        return false
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val isReleased = event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL
        val isPressed = event.action == MotionEvent.ACTION_DOWN
        if (isReleased) {
            stopUpdating()
        } else if (isPressed) {
            startUpdating(isIncrement(v), getStep(v))
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
                inc(dynamicMultiplier, stepGeneral, false)
            } else {
                dec(dynamicMultiplier, stepGeneral, false)
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
        private const val STEP = "step"
        private const val MULTIPLIER = "multiplier"
    }

    init {
        editText = binding.editText
        binding.label.text = label
        val format = DecimalFormat("#.#")
        binding.plusButton2?.text = "+${format.format(stepValues[1]).replaceFirst("^0+(?!$)".toRegex(), "")}"
        binding.plusButton3?.text = "+${format.format(stepValues[2]).replaceFirst("^0+(?!$)".toRegex(), "")}"

        value = initValue
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                val multiplier = msg.data.getInt(MULTIPLIER)
                val step = msg.data.getDouble(STEP)

                when (msg.what) {
                    MSG_INC -> {
                        inc(multiplier, step)
                        return
                    }

                    MSG_DEC -> {
                        dec(multiplier, step)
                        return
                    }
                }
                super.handleMessage(msg)
            }
        }

        editText.showSoftInputOnFocus = false
        editText.setTextIsSelectable(false)

        // Prevent editText clicks from propagating to ViewPager
        editText.isClickable = true
        editText.isFocusable = false
        editText.setOnClickListener {
            // Consume the click event without doing anything
            // This prevents the ViewPager from receiving the touch event
        }

        binding.minButton.setOnTouchListener(this)
        binding.minButton.setOnKeyListener(this)
        binding.minButton.setOnClickListener(this)
        binding.plusButton1.setOnTouchListener(this)
        binding.plusButton1.setOnKeyListener(this)
        binding.plusButton1.setOnClickListener(this)
        binding.plusButton2?.setOnTouchListener(this)
        binding.plusButton2?.setOnKeyListener(this)
        binding.plusButton2?.setOnClickListener(this)
        binding.plusButton3?.setOnTouchListener(this)
        binding.plusButton3?.setOnKeyListener(this)
        binding.plusButton3?.setOnClickListener(this)
        editText.setOnGenericMotionListener(this)
        updateEditText()
    }

}