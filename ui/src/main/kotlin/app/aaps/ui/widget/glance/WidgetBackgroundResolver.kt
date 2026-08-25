package app.aaps.ui.widget.glance

import android.graphics.Color
import app.aaps.core.interfaces.configuration.Config

fun resolveWidgetBackground(config: Config, useBlack: Boolean, alpha: Int): Int =
    when {
        config.APS || useBlack -> Color.argb(alpha, 0, 0, 0)
        config.AAPSCLIENT1     -> Color.argb(alpha, 0xE8, 0xC5, 0x0C)
        config.AAPSCLIENT2     -> Color.argb(alpha, 0x0F, 0xBB, 0xE0)
        config.AAPSCLIENT3     -> Color.argb(alpha, 0x4C, 0xAF, 0x50)
        else                   -> Color.argb(alpha, 0, 0, 0)
    }

/**
 * Follower-build identifier color used as a tint hint when the widget has a
 * transparent background (e.g. the Small widget). Returns white for the
 * full APS/loop build since there is no follower to distinguish.
 */
fun resolveClientColor(config: Config): Int =
    when {
        config.AAPSCLIENT1 -> Color.rgb(0xE8, 0xC5, 0x0C)
        config.AAPSCLIENT2 -> Color.rgb(0x0F, 0xBB, 0xE0)
        config.AAPSCLIENT3 -> Color.rgb(0x4C, 0xAF, 0x50)
        else               -> Color.WHITE
    }
