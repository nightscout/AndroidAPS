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
    @DrawableRes val tbrIconResId: Int,
    @ColorInt val backgroundColor: Int,
    // Spoken names for the icons that a screen reader would otherwise get nothing from. Resolved
    // here rather than in the widget, because everything else the widget shows is resolved here too
    // and a Glance composable has no injected resources of its own.
    //
    // The trend arrow and the temp basal icon have NO text beside them at all, so without these they
    // were silent. The insulin and carb icons have a value beside them but no noun, so a screen
    // reader read out a bare "1.20 U" with nothing saying what it measured.
    val arrowDescription: String?,
    val tbrDescription: String,
    val iobLabel: String,
    val cobLabel: String
)
