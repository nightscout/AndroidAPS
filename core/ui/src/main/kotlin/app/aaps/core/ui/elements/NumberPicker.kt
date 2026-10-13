package app.aaps.core.ui.elements

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import app.aaps.core.ui.R
import app.aaps.core.ui.databinding.NumberPickerLayoutBinding
import app.aaps.core.ui.toast.ToastUtils
import java.text.NumberFormat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.round

@SuppressLint("ClickableViewAccessibility")
open class NumberPicker(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs), View.OnKeyListener, OnTouchListener, View.OnClickListener {

    fun interface OnValueChangedListener {

        fun onValueChanged(value: Double)
    }

    var currentValue = 0.0
    var minValue = 0.0
    var maxValue = 1.0
    var step = 1.0
    var formatter: NumberFormat? = null
    var allowZero = false
    private var watcher: TextWatcher? = null
    var okButton: Button? = null
    protected var focused = false
    private var mUpdater: ScheduledExecutorService? = null
    private var mOnValueChangedListener: OnValueChangedListener? = null
    private var mCustomContentDescription: String? = null
    protected lateinit var binding: NumberPickerViewAdapter

    private var handler: Handler = Handler(Looper.getMainLooper(), Handler.Callback { msg: Message ->
        when (msg.what) {
            MSG_INC -> {
                inc(msg.arg1)
                return@Callback true
            }

            MSG_DEC -> {
                dec(msg.arg1)
                return@Callback true
            }
        }
        false
    })

    private fun Boolean.toVisibility() = if (this) VISIBLE else GONE
    private fun stringToDouble(inputString: String?, defaultValue: Double = 0.0): Double {
        var input = inputString ?: return defaultValue
        var result = defaultValue
        input = input.replace(",", ".")
        input = input.replace("−", "-")
        if (input == "") return defaultValue
        try {
            result = input.toDouble()
        } catch (_: Exception) {
//            log.error("Error parsing " + input + " to double");
        }
        return result
    }

    private inner class UpdateCounterTask(private val mInc: Boolean) : Runnable {

        private var repeated = 0
        private var multiplier = 1
        private val doubleLimit = 5
        override fun run() {
            val msg = Message()
            if (repeated % doubleLimit == 0) multiplier *= 2
            repeated++
            msg.arg1 = multiplier
            msg.arg2 = repeated
            if (mInc) {
                msg.what = MSG_INC
            } else {
                msg.what = MSG_DEC
            }
            handler.sendMessage(msg)
        }
    }

    val editTextId get() = binding.editText.id

    var customContentDescription: String?
        get() = mCustomContentDescription
        set(value) {
            mCustomContentDescription = value
            updateA11yDescription()
        }

    protected open fun inflate(context: Context) {
        val inflater = LayoutInflater.from(context)
        val bindLayout = NumberPickerLayoutBinding.inflate(inflater, this, true)
        binding = NumberPickerViewAdapter.getBinding(bindLayout)
    }

    protected fun initialize(context: Context) {
        // set layout view
        inflate(context)

        binding.minusButton.id = generateViewId()
        binding.plusButton.id = generateViewId()
        binding.editText.id = generateViewId()
        binding.minusButton.setOnTouchListener(this)
        binding.minusButton.setOnKeyListener(this)
        binding.minusButton.setOnClickListener(this)
        binding.plusButton.setOnTouchListener(this)
        binding.plusButton.setOnKeyListener(this)
        binding.plusButton.setOnClickListener(this)
        setTextWatcher(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (focused) currentValue = stringToDouble(binding.editText.text.toString())
                callValueChangedListener()
                val inValid = currentValue > maxValue || currentValue < minValue
                okButton?.visibility = inValid.not().toVisibility()
                binding.textInputLayout.error = if (inValid) "invalid" else null
            }
        })
        binding.editText.setOnFocusChangeListener { _: View?, hasFocus: Boolean ->
            focused = hasFocus
            if (!focused) value // check min/max
            updateEditText()
        }
        updateA11yDescription()
    }

    fun updateA11yDescription() {
        val description = mCustomContentDescription ?: ""
        binding.minusButton.contentDescription = context.getString(R.string.a11y_min_button_description, description, formatter?.format(this.step))
        binding.plusButton.contentDescription = context.getString(R.string.a11y_plus_button_description, description, formatter?.format(this.step))
    }

    fun announceValue() {
        val manager: AccessibilityManager = context
            .getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (manager.isEnabled) {
            val valueDescription = formatter?.format(currentValue)
            @Suppress("DEPRECATION")
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AccessibilityEvent()
            else AccessibilityEvent.obtain())
                .apply {
                    eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
                    className = javaClass.name
                    packageName = context.packageName
                    text.add(valueDescription)
                }.also {
                    manager.sendAccessibilityEvent(it)
                }
        }
    }

    override fun setTag(tag: Any) {
        binding.editText.tag = tag
    }

    fun setOnValueChangedListener(onValueChangedListener: OnValueChangedListener?) {
        mOnValueChangedListener = onValueChangedListener
    }

    fun setTextWatcher(textWatcher: TextWatcher) {
        watcher = textWatcher
        binding.editText.addTextChangedListener(textWatcher)
        binding.editText.onFocusChangeListener = OnFocusChangeListener { _: View?, hasFocus: Boolean ->
            if (!hasFocus) {
                currentValue = stringToDouble(binding.editText.text.toString())
                if (currentValue > maxValue) {
                    currentValue = maxValue
                    ToastUtils.warnToast(context, R.string.you_are_on_allowed_limit)
                    updateEditText()
                    okButton?.visibility = VISIBLE
                }
                if (currentValue < minValue) {
                    currentValue = minValue
                    ToastUtils.warnToast(context, R.string.you_are_on_allowed_limit)
                    updateEditText()
                    okButton?.visibility = VISIBLE
                }
            }
        }
    }

    fun setParams(initValue: Double, minValue: Double, maxValue: Double, step: Double, formatter: NumberFormat?, allowZero: Boolean, okButton: Button?, textWatcher: TextWatcher?) {
        if (watcher != null) {
            binding.editText.removeTextChangedListener(watcher)
        }
        setParams(initValue, minValue, maxValue, step, formatter, allowZero, okButton)
        watcher = textWatcher
        if (textWatcher != null) binding.editText.addTextChangedListener(textWatcher)
    }

    fun setParams(initValue: Double, minValue: Double, maxValue: Double, step: Double, formatter: NumberFormat?, allowZero: Boolean, okButton: Button?) {
        currentValue = initValue
        this.minValue = minValue
        this.maxValue = maxValue
        this.step = step
        this.formatter = formatter
        this.allowZero = allowZero
        callValueChangedListener()
        this.okButton = okButton
        binding.editText.keyListener = DigitsKeyListenerWithComma.getInstance(minValue < 0, step != round(step))
        if (watcher != null) binding.editText.removeTextChangedListener(watcher)
        updateA11yDescription()
        updateEditText()
        if (watcher != null) binding.editText.addTextChangedListener(watcher)
    }

    var value: Double
        get() {
            if (currentValue > maxValue) {
                currentValue = maxValue
                ToastUtils.warnToast(context, R.string.you_are_on_allowed_limit)
            }
            if (currentValue < minValue) {
                currentValue = minValue
                ToastUtils.warnToast(context, R.string.you_are_on_allowed_limit)
            }
            return currentValue
        }
        set(value) {
            if (watcher != null) binding.editText.removeTextChangedListener(watcher)
            currentValue = value
            if (currentValue > maxValue) {
                currentValue = maxValue
                ToastUtils.warnToast(context, R.string.you_are_on_allowed_limit)
            }
            if (currentValue < minValue) {
                currentValue = minValue
                ToastUtils.warnToast(context, R.string.you_are_on_allowed_limit)
            }
            callValueChangedListener()
            updateEditText()
            if (watcher != null) binding.editText.addTextChangedListener(watcher)
        }

    val text: String
        get() = binding.editText.text.toString()

    private fun inc(multiplier: Int) {
        currentValue += step * multiplier
        if (currentValue > maxValue) {
            currentValue = maxValue
            callValueChangedListener()
            ToastUtils.warnToast(context, R.string.you_are_on_allowed_limit)
            stopUpdating()
        }
        updateEditText()
    }

    private fun dec(multiplier: Int) {
        currentValue -= step * multiplier
        if (currentValue < minValue) {
            currentValue = minValue
            callValueChangedListener()
            ToastUtils.warnToast(context, R.string.you_are_on_allowed_limit)
            stopUpdating()
        }
        updateEditText()
    }

    protected open fun updateEditText() {
        if (currentValue == 0.0 && !allowZero) binding.editText.setText("") else binding.editText.setText(formatter?.format(currentValue))
    }

    private fun callValueChangedListener() {
        mOnValueChangedListener?.onValueChanged(currentValue)
    }

    private fun startUpdating(inc: Boolean) {
        if (mUpdater != null) {
            //log.debug("Another executor is still active");
            return
        }
        mUpdater = Executors.newSingleThreadScheduledExecutor()
        mUpdater?.scheduleWithFixedDelay(
            UpdateCounterTask(inc), 200, 200,
            TimeUnit.MILLISECONDS
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopUpdating()
        handler.removeCallbacksAndMessages(null)
    }

    private fun stopUpdating() {
        mUpdater?.shutdownNow()
        mUpdater = null
        announceValue()
    }

    override fun onClick(v: View) {
        if (mUpdater == null) {
            val imm = context.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.editText.windowToken, 0)
            binding.editText.clearFocus()
            if (v === binding.plusButton) {
                inc(1)
            } else {
                dec(1)
            }
            announceValue()
        }
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        val isKeyOfInterest = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
        val isReleased = event.action == KeyEvent.ACTION_UP
        val isPressed = (event.action == KeyEvent.ACTION_DOWN)
        if (isKeyOfInterest && isReleased) {
            stopUpdating()
        } else if (isKeyOfInterest && isPressed) {
            startUpdating(v === binding.plusButton)
        }
        return false
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val isReleased = event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL
        val isPressed = event.action == MotionEvent.ACTION_DOWN
        if (isReleased) {
            stopUpdating()
        } else if (isPressed) {
            startUpdating(v === binding.plusButton)
        }
        return false
    }

    companion object {

        private const val MSG_INC = 0
        private const val MSG_DEC = 1
    }

    init {
        initialize(context)
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.NumberPicker,
            0, 0
        ).apply {

            try {
                mCustomContentDescription = getString(R.styleable.NumberPicker_customContentDescription)
            } finally {
                recycle()
            }
        }
    }
}