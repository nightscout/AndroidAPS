package info.nightscout.pump.medtrum.bindingadapters

import android.view.View
import androidx.databinding.BindingAdapter

@BindingAdapter("onSafeClick")
fun View.setOnSafeClickListener(clickListener: View.OnClickListener?) {
    clickListener?.also {
        setOnClickListener(OnSafeClickListener(it))
    } ?: setOnClickListener(null)
}
