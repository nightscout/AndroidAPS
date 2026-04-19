package app.aaps.ui.widget.glance

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

data class WidgetRenderState(
    val bgText: String,
    @ColorInt val bgColor: Int,
    val strikeThrough: Boolean,
    @DrawableRes val arrowResId: Int?,
    val deltaText: String,
    val timeAgoText: String,
    val iobText: String,
    val iobActive: Boolean,
    val cobText: String,
    val cobActive: Boolean,
    val tempTargetText: String,
    @ColorInt val tempTargetColor: Int,
    val tempTargetActive: Boolean,
    @DrawableRes val tempTargetIconResId: Int,
    val profileText: String,
    val profileModified: Boolean,
    @DrawableRes val profileIconResId: Int,
    @DrawableRes val iobIconResId: Int,
    @DrawableRes val cobIconResId: Int,
    val runningModeText: String,
    @ColorInt val runningModeColor: Int,
    @DrawableRes val runningModeIconResId: Int,
    val runningModeActive: Boolean,
    @DrawableRes val sensitivityIconResId: Int,
    val sensitivityText: String,
    @ColorInt val backgroundColor: Int
)
