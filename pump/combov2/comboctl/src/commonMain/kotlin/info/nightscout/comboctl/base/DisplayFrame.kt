package info.nightscout.comboctl.base

const val DISPLAY_FRAME_WIDTH = 96
const val DISPLAY_FRAME_HEIGHT = 32

// One frame consists of 96x32 pixels.
const val NUM_DISPLAY_FRAME_PIXELS = DISPLAY_FRAME_WIDTH * DISPLAY_FRAME_HEIGHT

/**
 * Class containing a 96x32 pixel black&white Combo display frame.
 *
 * These frames are sent by the Combo when it is operating
 * in the remote terminal (RT) mode.
 *
 * The pixels are stored in row-major order. One boolean equals
 * one pixel.
 *
 * Note that this is not the layout of the pixels as transmitted
 * by the Combo. Rather, the pixels are rearranged in a layout
 * that is more commonly used and easier to work with.
 *
 * @property displayFramePixels Pixels of the display frame to use.
 *           The array has to have exactly NUM_DISPLAY_FRAME_PIXELS
 *           booleans.
 */
data class DisplayFrame(val displayFramePixels: BooleanArray) : Iterable<Boolean> {
    /**
     * Number of display frame pixels.
     *
     * This mainly exists to make this class compatible with
     * code that operates on collections.
     */
    val size = NUM_DISPLAY_FRAME_PIXELS

    init {
        require(displayFramePixels.size == size)
    }

    /**
     * Returns the pixel at the given coordinates.
     *
     * @param x X coordinate. Valid range is 0..95 (inclusive).
     * @param y Y coordinate. Valid range is 0..31 (inclusive).
     * @return true if the pixel at these coordinates is set,
     *         false if it is cleared.
     */
    fun getPixelAt(x: Int, y: Int) = displayFramePixels[x + y * DISPLAY_FRAME_WIDTH]

    operator fun get(index: Int) = displayFramePixels[index]

    override operator fun iterator() = displayFramePixels.iterator()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DisplayFrame

        return displayFramePixels.contentEquals(other.displayFramePixels)
    }

    override fun hashCode(): Int {
        return displayFramePixels.contentHashCode()
    }
}

/**
 * Display frame filled with empty pixels. Useful for initializations.
 */
val NullDisplayFrame = DisplayFrame(BooleanArray(NUM_DISPLAY_FRAME_PIXELS) { false })
