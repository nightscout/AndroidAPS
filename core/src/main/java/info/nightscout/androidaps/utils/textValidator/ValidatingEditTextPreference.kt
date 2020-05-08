package info.nightscout.androidaps.utils.textValidator

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreference.OnBindEditTextListener
import androidx.preference.PreferenceViewHolder
import info.nightscout.androidaps.core.R

class ValidatingEditTextPreference(ctx: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int)
    : EditTextPreference(ctx, attrs, defStyleAttr, defStyleRes) {

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FormEditText, 0, 0)
        val onBindEditTextListener = OnBindEditTextListener { editText ->
            editTextValidator = DefaultEditTextValidator(editText, typedArray, context)
        }
        setOnBindEditTextListener(onBindEditTextListener)
    }

    constructor(ctx: Context, attrs: AttributeSet, defStyle: Int)
        : this(ctx, attrs, defStyle, 0)

    constructor(ctx: Context, attrs: AttributeSet)
        : this(ctx, attrs, R.attr.editTextPreferenceStyle)

    private lateinit var editTextValidator: EditTextValidator

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        holder?.isDividerAllowedAbove = false
        holder?.isDividerAllowedBelow = false
    }
}
