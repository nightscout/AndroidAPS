package info.nightscout.comboctl.base

// Combo frames are delimited by the FRAME_DELIMITER byte 0xCC. Each
// frame begins and ends with this byte. Binary payload itself can
// also contain that byte, however. To solve this, there is the
// ESCAPE_BYTE 0x77. If payload itself contains a 0xCC byte, it is
// escaped by replacing that byte with the 2-byte sequence 0x77 0xDD.
// If the escape byte 0x77 itself is in the payload, then that byte
// is replaced by the 2-byte sequence 0x77 0xEE.
private const val FRAME_DELIMITER = 0xCC.toByte()
private const val ESCAPE_BYTE = 0x77.toByte()
private const val ESCAPED_FRAME_DELIMITER = 0xDD.toByte()
private const val ESCAPED_ESCAPE_BYTE = 0xEE.toByte()

/**
 * Exception thrown when parsing a Combo frame fails.
 *
 * @param message The detail message.
 */
class FrameParseException(message: String) : ComboException(message)

/**
 * Parses incoming streaming data to isolate frames and extract their payload.
 *
 * The Combo uses RFCOMM for communication, which is a stream based communication
 * channel, not a datagram based one. Therefore, it is necessary to use some sort
 * of framing mechanism to denote where frames begin and end.
 *
 * This class parses incoming data to detect the beginning of a frame. Then, it
 * continues to parse data until it detects the end of the current frame. At that
 * point, it extracts the payload from inside that frame, and returns it. This is
 * done by [parseFrame].
 *
 * The payload is a transport layer packet. See [TransportLayerIO.Packet] for details
 * about those.
 */
class ComboFrameParser {
    /**
     * Resets the internal state of the parser, discarding any accumulated data.
     *
     * This readies the parser for an entirely new transmission.
     */
    fun reset() {
        accumulationBuffer.clear()
        currentReadOffset = 0
        frameStarted = false
        frameStartOffset = 0
    }

    /**
     * Pushes incoming data into the parser's accumulation buffer for parsing.
     *
     * Call this only when [parseFrame] returns null, since incoming data blocks
     * may contain more than one frame, meaning that the data passed to one
     * [pushData] call may allow for multiple [parseFrame] calls that return
     * parsed payload.
     *
     * @param data Data to push into the parser.
     */
    fun pushData(data: List<Byte>) {
        accumulationBuffer.addAll(data)
    }

    /**
     * Parses previously accumulated data and extracts a frame if one is detected.
     *
     * This searches the accumulated data for a frame delimiter character. If one
     * is found, the parser continues to look for a second delimiter. These two
     * delimiters then denote the beginning and end of a frame. At that point, this
     * function will extract the payload in between the delimiters.
     *
     * This function also takes care of un-escaping characters if necessary.
     *
     * If there currently is no complete frame in the accumulation buffer, this
     * returns null. In that case, the user is supposed to call [pushData] to place
     * more data into the accumulation buffer to parse.
     *
     * @return Payload of a detected frame, or null if no complete frame was
     *         currently found.
     * @throws FrameParseException in case of invalid data.
     */
    fun parseFrame(): List<Byte>? {
        // The part that begins at currentReadOffset is not yet parsed.
        // Look through it to see if a frame delimiter can be found.
        while (currentReadOffset < accumulationBuffer.size) {
            // Get the next byte. We use readNextFrameByte() here to handle
            // escaped bytes.
            val currentByteInfo = readNextFrameByte(currentReadOffset)

            // Get the current byte. It is set to null when readNextFrameByte()
            // finds an escape byte, but there's currently no more data after
            // that byte. See readNextFrameByte() for more about this.
            val currentByte: Byte = currentByteInfo.first ?: return null
            val currentByteWasEscaped = currentByteInfo.third

            val oldReadOffset = currentReadOffset
            currentReadOffset = currentByteInfo.second

            if (frameStarted) {
                // The start of a frame was previously detected. Continue
                // to parse data until either the end of the current accumulated
                // data is reached or a frame delimiter byte is found.

                if (currentByte == FRAME_DELIMITER) {
                    // Found a frame delimiter byte. If it wasn't present as
                    // an escaped byte, it means it actually does delimit
                    // the end of the current frame. if so, extract its payload.

                    if (currentByteWasEscaped) {
                        // In this case, the byte was escaped, so it is part
                        // of the payload, and not the end of the current frame.
                        continue
                    }

                    // Extract the frame's payload, un-escaping any escaped
                    // bytes inside (done by the readNextFrameByte() call).
                    val framePayload = ArrayList<Byte>()
                    val frameEndOffset = currentReadOffset - 1
                    var frameReadOffset = frameStartOffset
                    while (frameReadOffset < frameEndOffset) {
                        val nextByteInfo = readNextFrameByte(frameReadOffset)
                        frameReadOffset = nextByteInfo.second
                        framePayload.add(nextByteInfo.first!!)
                    }

                    // After extracting the frame, remove its data from the
                    // accumulation buffer and keep track of what data is left
                    // in there. We need to keep that remainder around, since
                    // it may be the start of a new frame.
                    accumulationBuffer.subList(0, currentReadOffset).clear()

                    frameStarted = false
                    currentReadOffset = 0

                    return framePayload
                }
            } else {
                // No frame start was detected so far. Combo transmissions
                // pack frames seamlessly together, meaning that as soon as
                // a frame delimiter denotes the end of a frame, or an RFCOMM
                // connection is freshly set up, the next byte can only be
                // a frame delimiter. Anything else would indicate bogus data.
                // Also, in this case, we don't assume that bytes could be
                // escaped, since escaping only makes sense _within_ a frame.

                if (currentByte == FRAME_DELIMITER) {
                    frameStarted = true
                    frameStartOffset = currentReadOffset
                    continue
                } else {
                    throw FrameParseException(
                        "Found non-delimiter byte ${currentByte.toHexString(2)} " +
                        "outside of frames (surrounding context: ${accumulationBuffer.toHexStringWithContext(oldReadOffset)})"
                    )
                }
            }
        }

        return null
    }

    private fun readNextFrameByte(readOffset: Int): Triple<Byte?, Int, Boolean> {
        // The code here reads bytes from the accumulation buffer. If the
        // start of a frame was detected, it also handles un-escaping
        // escaped bytes (see the explanation at the top of this source).
        // It returns a triple of the parsed byte, the updated read offset,
        // and a boolean that is true if the read byte was present in escaped
        // escaped form in the accumulated data. The latter is important to
        // be able to distinguish actual frame delimiters and escape bytes
        // from payload bytes that happen to have the same value as these
        // special bytes. In case no byte could be parsed, null is returned.

        // We can't read anything, since there is no data that can be parsed.
        // Return null as byte value to indicate to the caller that we need
        // to accumulate more data.
        if (readOffset >= accumulationBuffer.size)
            return Triple(null, readOffset, false)

        val curByte = accumulationBuffer[readOffset]

        // If we are outside of a frame, just return the current byte directly.
        // Outside of a frame, only frame delimiter bytes should exist anyway.
        if (!frameStarted)
            return Triple(curByte, readOffset + 1, false)

        return if (curByte == ESCAPE_BYTE) {
            // We found an escape byte. We need the next byte to see what
            // particular byte was escaped by it.

            if (readOffset == (accumulationBuffer.size - 1)) {
                // Can't determine the output yet since we need 2 bytes to determine
                // what the output should be, and currently we only have one byte;
                // the escape byte. Return null as byte value to indicate to the
                // caller that we need to accumulate more data.
                Triple(null, readOffset, false)
            } else {
                when (accumulationBuffer[readOffset + 1]) {
                    ESCAPED_FRAME_DELIMITER -> Triple(FRAME_DELIMITER, readOffset + 2, true)
                    ESCAPED_ESCAPE_BYTE -> Triple(ESCAPE_BYTE, readOffset + 2, true)
                    else -> {
                        throw FrameParseException(
                            "Found escape byte, but followup byte ${accumulationBuffer[readOffset + 1].toHexString(2)} " +
                            "is not a valid combination (surrounding context: ${accumulationBuffer.toHexStringWithContext(readOffset + 1)})"
                        )
                    }
                }
            }
        } else {
            // This is not an escape byte, so just output it directly.
            Triple(curByte, readOffset + 1, false)
        }
    }

    private var accumulationBuffer = ArrayList<Byte>()
    private var currentReadOffset: Int = 0
    private var frameStarted: Boolean = false
    private var frameStartOffset: Int = 0
}

/**
 * Produces a Combo frame out of the given payload.
 *
 * The Combo uses RFCOMM for communication, which is a stream based communication
 * channel, not a datagram based one. Therefore, it is necessary to use some sort
 * of framing mechanism to denote where frames begin and end.
 *
 * This function places payload (a list of bytes) inside a frame so that it is
 * suitable for transmission over RFCOMM.
 *
 * The reverse functionality is provided by the [ComboFrameParser] class.
 *
 * The payload is a transport layer packet. See [TransportLayerIO.Packet] for
 * details about those.
 *
 * @return Framed version of this payload.
 */
fun List<Byte>.toComboFrame(): List<Byte> {
    val escapedFrameData = ArrayList<Byte>()

    escapedFrameData.add(FRAME_DELIMITER)

    for (inputByte in this) {
        when (inputByte) {
            FRAME_DELIMITER -> {
                escapedFrameData.add(ESCAPE_BYTE)
                escapedFrameData.add(ESCAPED_FRAME_DELIMITER)
            }

            ESCAPE_BYTE -> {
                escapedFrameData.add(ESCAPE_BYTE)
                escapedFrameData.add(ESCAPED_ESCAPE_BYTE)
            }

            else -> escapedFrameData.add(inputByte)
        }
    }

    escapedFrameData.add(FRAME_DELIMITER)

    return escapedFrameData
}
