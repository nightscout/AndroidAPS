package info.nightscout.pump.medtrum.bindingadapters

import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.databinding.BindingAdapter
import info.nightscout.pump.medtrum.extension.setVisibleOrGone

@BindingAdapter("android:visibility")
fun setVisibility(view: View, visible: Boolean) {
    view.setVisibleOrGone(visible)
}

@BindingAdapter("visibleOrGone")
fun setVisibleOrGone(view: View, visibleOrGone: Boolean) {
    view.setVisibleOrGone(visibleOrGone)
}

@BindingAdapter("onSafeClick")
fun View.setOnSafeClickListener(clickListener: View.OnClickListener?) {
    clickListener?.also {
        setOnClickListener(OnSafeClickListener(it))
    } ?: setOnClickListener(null)
}

@BindingAdapter("textColor")
fun setTextColor(view: TextView, @ColorRes colorResId: Int) {
    view.setTextColor(view.context.getColor(colorResId))
}
