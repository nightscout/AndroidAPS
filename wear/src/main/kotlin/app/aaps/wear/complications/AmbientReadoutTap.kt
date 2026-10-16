package app.aaps.wear.complications

/**
 * What a tap on one of the two always-on readouts should do.
 *
 * A rule of its own because it has broken twice, in opposite directions, and each fix caused the
 * other fault:
 *
 * - with an action in every mode, the **first tap on a sleeping watch opened AAPS** instead of
 *   waking the screen - reported twice from a Galaxy Watch 4, with the tap highlight visible on the
 *   picture;
 * - with no action in any mode, the readouts became a **dead spot on an awake watch** - a tap there
 *   did nothing, while a tap anywhere else on the face opened AAPS.
 *
 * Both halves are needed, and neither is obvious from the other's code. These slots sit over the
 * picture and take the tap wherever they are drawn, so they cannot simply let it fall through.
 *
 * The mode is read when the complication data is built, and that data outlives the mode it was built
 * in - so `CwfComplicationUpdater` asks these two for new data on every mode change. Without that
 * the rule is right and the wearer still gets the dead spot, just for a minute instead of for ever.
 *
 * @param dozing whether the watch is in its low-power always-on state
 */
fun readoutTapAction(dozing: Boolean): ComplicationAction =
    if (dozing) ComplicationAction.NONE else ComplicationAction.MENU
