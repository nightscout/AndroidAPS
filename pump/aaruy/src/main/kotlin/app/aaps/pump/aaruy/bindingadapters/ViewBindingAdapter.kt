package app.aaps.pump.aaruy.bindingadapters

import android.view.View
import androidx.databinding.BindingAdapter

@BindingAdapter("onSafeClick")
fun View.setOnClickListener(clickListener: View.OnClickListener?) {
    clickListener?.also {
        setOnClickListener(OnAaruySafeClickListener(it))
    } ?: setOnClickListener(null)
}
