package app.aaps.pump.medtrum.bindingadapters

import android.view.View
import androidx.databinding.BindingAdapter
import app.aaps.pump.medtrum.bindingadapters.OnSafeClickListener

@BindingAdapter("onSafeClick")
fun View.setOnSafeClickListener(clickListener: View.OnClickListener?) {
    clickListener?.also {
        setOnClickListener(OnSafeClickListener(it))
    } ?: setOnClickListener(null)
}
