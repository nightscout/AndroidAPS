package info.nightscout.comboctl.base

private const val NUM_DISPLAY_FRAME_BYTES = NUM_DISPLAY_FRAME_PIXELS / 8

/**
 * Class for assembling RT_DISPLAY application layer packet rows to a complete display frame.
 *
 * RT_DISPLAY packets contain 1/4th of a frame. These subsets are referred to as "rows".
 * Since a frame contains 96x32 pixels, each row contains 96x8 pixels.
 *
 * This class assembles these rows into complete frames. To that end, it has to convert
 * the layout the pixels are arranged in into a more intuitive column-major bitmap layout.
 * The result is a [DisplayFrame] instance with all of the frame's pixels in row-major
 * layout. See the [DisplayFrame] documentation for more details about its layout.
 *
 * The class is designed for streaming use. This means that it can be continuously fed
 * the contents of RT_DISPLAY packets, and it will keep producing frames once it has
 * enough data to complete a frame. When it completed one, it returns the frame, and
 * wipes its internal row collection, allowing it to start from scratch to be able to
 * begin completing a new frame.
 *
 * If frame data with a different index is fed into the assembler before the frame completion
 * is fully done, it also resets itself. The purpose of the index is to define what frame
 * each row belongs to. That way, it is assured that rows of different frames cannot be
 * mixed up together. Without the index, if for some reason one RT_DISPLAY packet isn't
 * received, the assembler would assemble a frame incorrectly.
 *
 * In practice, it is not necessary to keep this in mind. Just feed data into the assembler
 * by calling its main function, [DisplayFrameAssembler.processRTDisplayPayload]. When that
 * function returns null, just keep going. When it returns a [DisplayFrame] instance, then
 * this is a complete frame that can be further processed / analyzed.
 *
 * Example:
 *
 * ```
 * val assembler = DisplayFrameAssembler()
 *
 * while (receivingPackets()) {
 *     val rtDisplayPayload = applicationLayer.parseRTDisplayPacket(packet)
 *
 *     val displayFrame = assembler.processRTDisplayPayload(
 *         rtDisplayPayload.index,
 *         rtDisplayPayload.row,
 *         rtDisplayPayload.pixels
 *     )
 *     if (displayFrame != null) {
 *         // Output the completed frame
 *     }
 * }
 * ```
 */
class DisplayFrameAssembler {
    private val rtDisplayFrameRows = mutableListOf<List<Byte>?>(null, null, null, null)
    private var currentIndex: Int? = null
    private var numRowsLeftUnset = 4

    /**
     * Main assembly function.
     *
     * This feeds RT_DISPLAY data into the assembler. The index is the RT_DISPLAY
     * index value. The row is a value in the 0-3 range, specifying what row this
     * is about. rowBytes is the byte list containing the pixel bytes from the packet.
     * This list must contain exactly 96 bytes, since the whole frame is made of
     * 384 bytes, and there are 4 rows, so each row contains 384 / 4 = 96 bytes.
     *
     * (The incoming frame rows store pixels as bits, not as booleans, which is
     * why one frame consists of 384 bytes. 96 * 32 pixels, 8 bits per byte,
     * one bits per pixel -> 96 * 32 / 8 = 384 bytes.)
     *
     * @param index RT_DISPLAY index value.
     * @param row Row number, in the 0-3 range (inclusive).
     * @param rowBytes RT_DISPLAY pixel bytes.
     * @return null if no frame could be completed yet. A DisplayFrame instance
     *         if the assembler had enough data to complete a frame.
     */
    fun processRTDisplayPayload(index: Int, row: Int, rowBytes: List<Byte>): DisplayFrame? {
        require(rowBytes.size == NUM_DISPLAY_FRAME_BYTES / 4) {
            "Expected ${NUM_DISPLAY_FRAME_BYTES / 4} bytes in rowBytes list, got ${rowBytes.size} (index: $index row: $row)"
        }

        // Check if we got data from a different frame. If so, we have to throw
        // away any previously collected data, since it belongs to a previous frame.
        if (index != currentIndex) {
            reset()
            currentIndex = index
        }

        // If we actually are _adding_ a new row, decrement the numRowsLeftUnset
        // counter. That counter specifies how many row entries are still set to
        // null. Once the counter reaches zero, it means the rtDisplayFrameRows
        // list is fully populated, and we can complete the frame.
        if (rtDisplayFrameRows[row] == null)
            numRowsLeftUnset -= 1

        rtDisplayFrameRows[row] = rowBytes

        return if (numRowsLeftUnset == 0) {
            val displayFrame = assembleDisplayFrame()
            currentIndex = null
            displayFrame
        } else
            null
    }

    /**
     * Main assembly function.
     *
     * This is an overloaded variant of [processRTDisplayPayload] that accepts
     * an [ApplicationLayer.RTDisplayPayload] instance instead of the individual
     * index, row, pixels arguments.
     */
    fun processRTDisplayPayload(rtDisplayPayload: ApplicationLayer.RTDisplayPayload): DisplayFrame? =
        processRTDisplayPayload(rtDisplayPayload.index, rtDisplayPayload.row, rtDisplayPayload.rowBytes)

    /**
     * Resets the state of the assembler.
     *
     * This resets internal states to their initial values, discarding any
     * partial frames that might have been received earlier.
     *
     * Usually, this is only called internally. However, it is useful to call
     * this if the outside code itself got reset, for example after a reconnect
     * event. In such situations, it is a good idea to discard any existing state.
     */
    fun reset() {
        rtDisplayFrameRows.fill(null)
        numRowsLeftUnset = 4
    }

    private fun assembleDisplayFrame(): DisplayFrame {
        val displayFramePixels = BooleanArray(NUM_DISPLAY_FRAME_PIXELS) { false }

        // (Note: Display frame rows are not to be confused with pixel rows. See the
        // class description for details about the display frame rows.)
        // Pixels are stored in the RT_DISPLAY display frame rows in a column-major
        // order. Also, the rightmost column is actually stored first, and the leftmost
        // one last. And since each display frame row contains 1/4th of the entire display
        // frame, this means it contains 8 pixel rows. This in turn means that this
        // layout stores one byte per column. So, the first byte in the display frame row
        // contains the pixels from (x 95 y 0) to (x 95 y 7). The second byte contains
        // pixels from (x 94 y 0) to (x 94 y 7) etc.
        for (row in 0 until 4) {
            val rtDisplayFrameRow = rtDisplayFrameRows[row]!!
            for (column in 0 until DISPLAY_FRAME_WIDTH) {
                // Get the 8 pixels from the current column.
                // We invert the index by subtracting it from
                // 95, since, as described above, the first
                // byte actually contains the rightmost column.
                val byteWithColumnPixels = rtDisplayFrameRow[95 - column].toPosInt()
                // Scan the 8 pixels in the selected column.
                for (y in 0 until 8) {
                    // Isolate the current pixel.
                    val pixel = ((byteWithColumnPixels and (1 shl y)) != 0)

                    if (pixel) {
                        val destPixelIndex = column + (y + row * 8) * DISPLAY_FRAME_WIDTH
                        displayFramePixels[destPixelIndex] = true
                    }
                }
            }
        }

        return DisplayFrame(displayFramePixels)
    }
}
