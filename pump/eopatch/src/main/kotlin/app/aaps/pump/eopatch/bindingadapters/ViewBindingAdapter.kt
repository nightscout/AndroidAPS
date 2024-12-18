package app.aaps.pump.eopatch.bindingadapters

import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.databinding.BindingAdapter
import app.aaps.pump.eopatch.extension.check
import app.aaps.pump.eopatch.extension.setVisibleOrGone

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

@BindingAdapter("android:text")
fun setText(view: TextView, @StringRes resId: Int?) {
    val text = resId?.let { view.context.getString(it) } ?: ""
    val oldText = view.text
    if (text.check(oldText)) {
        view.text = text
    }
}