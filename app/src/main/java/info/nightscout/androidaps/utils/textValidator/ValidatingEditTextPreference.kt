package info.nightscout.androidaps.utils.textValidator

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreference.OnBindEditTextListener
import androidx.preference.PreferenceViewHolder
import info.nightscout.androidaps.R

class ValidatingEditTextPreference(private val ctx: Context, val attrs: AttributeSet, private val defStyleAttr: Int, private val defStyleRes: Int)
    : EditTextPreference(ctx, attrs, defStyleAttr, defStyleRes) {

    init {
        dialogLayoutResource = R.layout.dialog_preference
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FormEditText, 0, 0)
        val onBindEditTextListener = OnBindEditTextListener { editText ->
            editTextValidator = DefaultEditTextValidator(editText, typedArray, context)
        }
        setOnBindEditTextListener(onBindEditTextListener)
    }

    constructor(ctx: Context, attrs: AttributeSet, defStyle: Int)
        : this(ctx, attrs, defStyle, 0)

    constructor(ctx: Context, attrs: AttributeSet)
        : this(ctx, attrs, TypedArrayUtils.getAttr(ctx, R.attr.editTextPreferenceStyle,
        R.attr.editTextPreferenceStyle))

    lateinit var editTextValidator: EditTextValidator

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        holder?.isDividerAllowedAbove = false
        holder?.isDividerAllowedBelow = false
    }
}