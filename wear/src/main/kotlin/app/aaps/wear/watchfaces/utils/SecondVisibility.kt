package app.aaps.wear.watchfaces.utils

import android.view.View

/**
 * Whether the seconds are drawn, from the two facts that decide it and nothing else.
 *
 * A function rather than a line inside the watch face because of what happened when it was not one.
 * The rule used to read the view's current visibility and combine it with the mode, which made it a
 * latch: hidden once, hidden for ever, since the value it read was the value it had just written.
 *
 * @param declaredVisible whether the loaded zip asks for this view at all, and the wearer's
 *   preference allows it
 * @param showSecond whether seconds are shown at this moment - the zip enables them, the wearer has
 *   not switched them off, and the watch is awake
 */
fun secondVisibility(declaredVisible: Boolean, showSecond: Boolean): Int =
    if (declaredVisible && showSecond) View.VISIBLE else View.GONE

/**
 * Whether the seconds are shown at this moment.
 *
 * The wearer's own choice always wins: [enabledByUser] false means no seconds, whatever else says.
 * Beyond that the watch mode decides - except while a frame is being built for a picture, where the
 * mode can change between the moment the frame starts and the moment it is drawn.
 *
 * That last case is why [renderOverride] exists. A frame takes 200 ms to over a second to make, and
 * the wrist can drop in that time. The frame then went out with a second hand on it, the runtime
 * painted it in always-on, and because it repaints only about once a minute the hand could sit there
 * for the best part of a minute after the watch had gone dark - seen on a Galaxy Watch 4 as the
 * second hand moving one last time *after* the screen dimmed.
 *
 * The override is set only by the render path and only for the frame in hand, so on the live watch
 * face it is always null and this reduces exactly to `enabledByUser && interactive`, which is what
 * that face has always done.
 *
 * @param enabledByUser the zip enables seconds and the wearer has not switched them off
 * @param interactive the watch is awake
 * @param renderOverride null to follow [interactive]; otherwise the answer for this frame alone
 */
fun showSeconds(enabledByUser: Boolean, interactive: Boolean, renderOverride: Boolean?): Boolean =
    enabledByUser && (renderOverride ?: interactive)
