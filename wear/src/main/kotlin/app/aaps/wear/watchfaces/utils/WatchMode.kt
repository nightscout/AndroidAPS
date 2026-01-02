package app.aaps.wear.watchfaces.utils

/**
 * Modes in which the watch face can appear.
 */
enum class WatchMode {

    /**
     * When the user moves their wrist to glance at their watch, the watch face enters
     * Interactive mode. Full color and fluid animation is permitted.
     */
    INTERACTIVE,

    /**
     * Ambient mode helps the device conserve power. The watch face should only
     * display shades of gray, black, and white and update once per minute.
     */
    AMBIENT,

    /**
     * Low-Bit mode is a type of Ambient mode and is based on hardware considerations
     * (e.g. OLED, transflective LED screens). The watch face should only use black and white, avoid
     * grayscale colors, and disable anti-aliasing in paint styles.
     */
    LOW_BIT,

    /**
     * Burn-In Protection mode is a type of Ambient mode and is based on hardware considerations
     * (e.g. OLED screens). The watch face should not use large blocks of non-black pixels while keeping
     * ~95% of all pixels black.
     */
    BURN_IN,

    /**
     * Low-Bit + Burn-In Protection mode is a type of Ambient mode that is the combination of
     * Low-Bit and Burn-In Protection modes.
     */
    LOW_BIT_BURN_IN
}
