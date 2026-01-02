package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Payload which contains some bytes that equal "special" or "reserved" bytes.
// These bytes are 0xCC and 0x77.
val payloadDataWithSpecialBytes = byteArrayListOfInts(
    0x11, 0x22, 0x11,
    0xCC,
    0x11,
    0x77,
    0x44,
    0x77, 0xCC,
    0x00,
    0xCC, 0x77,
    0x55
)

// The frame version of the payload above, with the frame delimiter 0xCC at
// the beginning and end, plus the colliding payload bytes in escaped form.
val frameDataWithEscapedSpecialBytes = byteArrayListOfInts(
    0xCC,
    0x11, 0x22, 0x11,
    0x77, 0xDD, // 0x77 0xDD is the escaped form of 0xCC
    0x11,
    0x77, 0xEE, // 0xEE 0xDD is the escaped form of 0x77
    0x44,
    0x77, 0xEE, 0x77, 0xDD,
    0x00,
    0x77, 0xDD, 0x77, 0xEE,
    0x55,
    0xCC
)

class ComboFrameTest : TestBase() {

    @Test
    fun produceEscapedFrameData() {
        // Frame the payload and check that the framing is done correctly.

        val producedEscapedFrameData = payloadDataWithSpecialBytes.toComboFrame()
        assertEquals(frameDataWithEscapedSpecialBytes, producedEscapedFrameData)
    }

    @Test
    fun parseEscapedFrameData() {
        // Parse escaped frame data and check that the original payload is recovered.

        val parser = ComboFrameParser()
        parser.pushData(frameDataWithEscapedSpecialBytes)

        val parsedPayloadData = parser.parseFrame()
        assertTrue(parsedPayloadData != null)
        assertEquals(payloadDataWithSpecialBytes, parsedPayloadData)
    }

    @Test
    fun parsePartialFrameData() {
        // Frame data can come in partial chunks. The parser has to accumulate the
        // chunks and try to find a complete frame within the accumulated data.
        // If none can be found, parseFrame() returns null. Otherwise, it extracts
        // the data within the two delimiters of the frame, un-escapes any escaped
        // bytes, and returns the recovered payload from that frame.

        val parser = ComboFrameParser()

        // Three chunks of partial frame data. Only after all three have been pushed
        // into the parser can it find a complete frame. In fact, it then has
        // accumulated data containing even _two_ complete frames.

        // The first chunk, which starts the first frame. No complete frame yet.
        val partialFrameData1 = byteArrayListOfInts(
            0xCC,
            0x11, 0x22, 0x33
        )
        // Next chunk, contains more bytes of the first frame, but still no end
        // of that frame.
        val partialFrameData2 = byteArrayListOfInts(
            0x44, 0x55
        )
        // Last chunk. It not only contains the second delimiter of the first frame,
        // but also a complete second frame.
        val partialFrameData3 = byteArrayListOfInts(
            0xCC,
            0xCC,
            0x66, 0x88, 0x99,
            0xCC
        )
        // The two frames contained in the three chunks above have these two payloads.
        val payloadFromPartialData1 = byteArrayListOfInts(0x11, 0x22, 0x33, 0x44, 0x55)
        val payloadFromPartialData2 = byteArrayListOfInts(0x66, 0x88, 0x99)

        // Push the first chunk into the parsed frame. We don't expect
        // it to actually parse something yet.
        parser.pushData(partialFrameData1)
        var parsedPayloadData = parser.parseFrame()
        assertEquals(null, parsedPayloadData)

        // Push the second chunk. We still don't expect a parsed frame,
        // since the second chunk does not complete the first frame yet.
        parser.pushData(partialFrameData2)
        parsedPayloadData = parser.parseFrame()
        assertEquals(null, parsedPayloadData)

        // Push the last chunk. With that chunk, the parser accumulated
        // enough data to parse the first frame and an additional frame.
        // Therefore, we expect the next two parseFrame() calls to
        // return a non-null value - the expected payloads.
        parser.pushData(partialFrameData3)
        parsedPayloadData = parser.parseFrame()
        assertEquals(payloadFromPartialData1, parsedPayloadData!!)
        parsedPayloadData = parser.parseFrame()
        assertEquals(payloadFromPartialData2, parsedPayloadData!!)

        // There is no accumulated data left for parsing, so we
        // expect the parseFrame() call to return null.
        parsedPayloadData = parser.parseFrame()
        assertEquals(null, parsedPayloadData)
    }

    @Test
    fun parsePartialFrameDataWithSpecialBytes() {
        // Test the parser with partial chunks again. But this time,
        // the payload within the frames contain special bytes, so
        // the chunks contain escaped bytes.

        val parser = ComboFrameParser()

        // First partial chunk. It ends with an escape byte (0x77). The
        // parser cannot do anything with that escape byte alone, since
        // only with a followup byte can it be determined what byte was
        // escaped.
        val partialFrameDataWithSpecialBytes1 = byteArrayListOfInts(
            0xCC,
            0x11, 0x22, 0x77
        )
        // Second partial chunk. Completes the frame, and provides the
        // missing byte that, together with the escape byte from the
        // previous chunk, combines to 0x77 0xEE, which is the escaped
        // form of the payload byte 0x77.
        val partialFrameDataWithSpecialBytes2 = byteArrayListOfInts(
            0xEE, 0x33, 0xCC
        )
        // The payload in the frame that is transported over the chunks.
        val payloadFromPartialDataWithSpecialBytes = byteArrayListOfInts(0x11, 0x22, 0x77, 0x33)

        // Push the first chunk. We don't expect the parser to return
        // anything yet.
        parser.pushData(partialFrameDataWithSpecialBytes1)
        var parsedPayloadData = parser.parseFrame()
        assertEquals(null, parsedPayloadData)

        // Push the second chunk. The frame is now complete. The parser
        // should now find the frame and extract the payload, in correct
        // un-escaped form.
        parser.pushData(partialFrameDataWithSpecialBytes2)
        parsedPayloadData = parser.parseFrame()
        assertEquals(payloadFromPartialDataWithSpecialBytes, parsedPayloadData!!)

        // There is no accumulated data left for parsing, so we
        // expect the parseFrame() call to return null.
        parsedPayloadData = parser.parseFrame()
        assertEquals(null, parsedPayloadData)
    }

    @Test
    fun parseNonDelimiterOutsideOfFrame() {
        // Outside of frames, only the frame delimiter byte 0x77 is expected.
        // That's because the Combo frames are tightly packed, like this:
        //
        // 0xCC <frame 1 bytes> 0xCC  0xCC <frame 2 bytes> 0xCC ...

        val parser = ComboFrameParser()

        // This is invalid data, since 0x11 lies before the 0xCC frame delimiter,
        // meaning that the 0x11 byte is "outside of a frame".
        val frameDataWithNonDelimiterOutsideOfFrame = byteArrayListOfInts(
            0x11, 0xCC
        )

        parser.pushData(frameDataWithNonDelimiterOutsideOfFrame)

        assertFailsWith<FrameParseException> { parser.parseFrame() }
    }

    @Test
    fun parseInvalidEscapeByteCombination() {
        // In frame data, the escape byte 0x77 is followed by the byte 0xDD
        // or 0xEE (the escaped form of bytes 0xCC and 0x77, respectively).
        // Any other byte immediately following 0x77 is invalid, since no
        // escaping is defined then. For example, 0x77 0x88 does not describe
        // anything.

        val parser = ComboFrameParser()

        // 0x77 0xAA is an invalid sequence.
        val frameDataWithInvalidEscapeByteCombination = byteArrayListOfInts(
            0xCC, 0x77, 0xAA, 0xCC
        )

        parser.pushData(frameDataWithInvalidEscapeByteCombination)

        assertFailsWith<FrameParseException> { parser.parseFrame() }
    }
}
