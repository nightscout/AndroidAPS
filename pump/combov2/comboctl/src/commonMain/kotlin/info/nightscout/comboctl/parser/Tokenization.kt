package info.nightscout.comboctl.parser

import info.nightscout.comboctl.base.DISPLAY_FRAME_HEIGHT
import info.nightscout.comboctl.base.DISPLAY_FRAME_WIDTH
import info.nightscout.comboctl.base.DisplayFrame
import kotlin.math.sign

/**
 * Structure containing details about a match discovered in a [DisplayFrame].
 *
 * The match is referred to as a "token", similar to lexical tokens
 * in lexical analyzers.
 *
 * This is the result of a pattern search in a display frame.
 *
 * @property pattern The pattern for which a match was found.
 * @property glyph [Glyph] associated with the pattern.
 * @property x X-coordinate of the location of the token in the display frame.
 * @property y Y-coordinate of the location of the token in the display frame.
 */
data class Token(
    val pattern: Pattern,
    val glyph: Glyph,
    val x: Int,
    val y: Int
)

/**
 * List of tokens found in the display frame by the [findTokens] function.
 */
typealias Tokens = List<Token>

/**
 * Checks if the region at the given coordinates matches the given pattern.
 *
 * This is used for finding tokerns in a frame.
 *
 * @param displayFrame [DisplayFrame] that contains the region to match the pattern against.
 * @param pattern Pattern to match with the region in the display frame.
 * @param x X-coordinate of the region in the display frame.
 * @param y Y-coordinate of the region in the display frame.
 * @return true if the region matches the pattern. false in case of mismatch
 *         or if the coordinates would place the pattern (partially) outside
 *         of the bounds of the display frame.
 */
fun checkIfPatternMatchesAt(displayFrame: DisplayFrame, pattern: Pattern, x: Int, y: Int): Boolean {
    if ((x < 0) || (y < 0) ||
        ((x + pattern.width) > DISPLAY_FRAME_WIDTH) ||
        ((y + pattern.height) > DISPLAY_FRAME_HEIGHT))
        return false

    // Simple naive brute force match.
    // TODO: See if a two-dimensional variant of the Boyer-Moore algorithm can be used instead.

    for (py in 0 until pattern.height) {
        for (px in 0 until pattern.width) {
            val patternPixel = pattern.pixels[px + py * pattern.width]
            val framePixel = displayFrame.getPixelAt(
                x + px,
                y + py
            )

            if (patternPixel != framePixel)
                return false
        }
    }

    return true
}

/**
 * Look for regions in the display frame that can  be turned into tokens.
 *
 * This will first do a pattern matching search, and then try to filter out matches that
 * overlap with other matches and are considered to be unnecessary / undesirable by the
 * internal heuristic (for example, a part of the multiwave bolus pattern also looks like
 * the character L, but in case of such an overlap, we are interested in the former).
 * The remaining matches are output as tokens.
 *
 * @param displayFrame [DisplayFrame] to search for tokens.
 * @return Tokens found in this frame.
 */
fun findTokens(displayFrame: DisplayFrame): Tokens {
    val tokens = mutableListOf<Token>()

    // Scan through the display frame and look for tokens.

    var y = 0

    while (y < DISPLAY_FRAME_HEIGHT) {
        var x = 0

        while (x < DISPLAY_FRAME_WIDTH) {
            for ((glyph, pattern) in glyphPatterns) {
                if (checkIfPatternMatchesAt(displayFrame, pattern, x, y)) {
                    // Current region in the display frame matches this pattern.
                    // Create a token out of the pattern, glyph, and coordinates,
                    // add the token to the list of found tokens, and move past the
                    // matched pattern horizontally. (There's no point in advancing
                    // pixel by pixel horizontally since the next pattern.width pixels
                    // are guaranteed to be part of the already discovered token).
                    tokens.add(Token(pattern, glyph, x, y))
                    x += pattern.width - 1 // -1 since the x value is incremented below.
                    break
                }
            }

            x++
        }

        y++
    }

    // Check for overlaps. The pattern matching is not automatically unambiguous.
    // For example, one of the corners of the multiwave bolus icon also matches
    // the small 'L' character pattern. Try to resolve overlaps here and remove
    // tokens if required. (In the example above, the 'L' pattern match is not
    // needed and can be discarded - the match of interest there is the multiwave
    // bolus icon pattern match.)

    val tokensToRemove = mutableSetOf<Token>()

    // First, determine what tokens to remove.
    for (tokenB in tokens) {
        for (tokenA in tokens) {
            // Get the coordinates of the top-left (x1,y1) and bottom-right (x2,y2)
            // corners of the bounding rectangles of both matches. The (x2,y2)
            // coordinates are inclusive, that is, still inside the rectangle, and
            // at the rectangle's bottom right corner. (That's why there's the -1
            // in the calculations below; it avoids a fencepost error.)

            val tokenAx1 = tokenA.x
            val tokenAy1 = tokenA.y
            val tokenAx2 = tokenA.x + tokenA.pattern.width - 1
            val tokenAy2 = tokenA.y + tokenA.pattern.height - 1

            val tokenBx1 = tokenB.x
            val tokenBy1 = tokenB.y
            val tokenBx2 = tokenB.x + tokenB.pattern.width - 1
            val tokenBy2 = tokenB.y + tokenB.pattern.height - 1

            /* Overlap detection:

            Given two rectangles A and B:

            Example of non-overlap:

            <                  A                  >
                                                            <                  B                  >
                                                  |---xd2---|

            |-----------------------------------------xd1-----------------------------------------|

            Example of overlap:

            <                  A                  >
                          <                  B                  >
                          |----------xd2----------|

            |------------------------xd1------------------------|

            xd1 = distance from A.x1 to B.x2
            xd2 = distance from A.x2 to B.x1

            If B is fully to the right of A, then both xd1 and xd2 are positive.
            If B is fully to the left of A, then both xd1 and xd2 are negative.
            If xd1 is positive and xd2 is negative (or vice versa), then A and B are overlapping in the X direction.

            The same tests are done in Y direction.

            If A and B overlap in both X and Y direction, they overlap overall.

            It follows that:
            if (xd1 is positive and xd2 is negative) or (xd1 is negative and xd2 is positive) and
               (yd1 is positive and yd2 is negative) or (yd1 is negative and yd2 is positive) -> A and B overlap.

            The (xd1 is positive and xd2 is negative) or (xd1 is negative and xd2 is positive) check
            can be shorted to: (sign(xd1) != sign(xd2)). The same applies to the checks in the Y direction.

            -> Final check: if (sign(xd1) != sign(xd2)) and (sign(yd1) != sign(yd2)) -> A and B overlap.
            */

            val xd1 = (tokenBx2 - tokenAx1)
            val xd2 = (tokenBx1 - tokenAx2)
            val yd1 = (tokenBy2 - tokenAy1)
            val yd2 = (tokenBy1 - tokenAy2)

            val tokensOverlap = (xd1.sign != xd2.sign) && (yd1.sign != yd2.sign)

            if (tokensOverlap) {
                // Heuristic for checking if one of the two overlapping tokens
                // needs to be removed:
                //
                // 1. If one token has a large pattern and the other doesn't,
                //    keep the large one and discard the smaller one. Parts of larger
                //    patterns can be misintepreted as some of the smaller patterns,
                //    which is the reason for this heuristic.
                // 2. If one token has a larger numSetPixels value than the other,
                //    pick that one. A higher number of set pixels is considered to
                //    indicate a more "complex" or "informative" pattern. For example,
                //    the 2 blocks of 2x2 pixels at the top ends of the large 'U'
                //    character token can also be interpreted as a large dot token.
                //    However, the large dot token has 4 set pixels, while the large
                //    'U' character token has many more, so the latter "wins".
                if (tokenA.glyph.isLarge && !tokenB.glyph.isLarge)
                    tokensToRemove.add(tokenB)
                else if (!tokenA.glyph.isLarge && tokenB.glyph.isLarge)
                    tokensToRemove.add(tokenA)
                else if (tokenA.pattern.numSetPixels > tokenB.pattern.numSetPixels)
                    tokensToRemove.add(tokenB)
                else if (tokenA.pattern.numSetPixels < tokenB.pattern.numSetPixels)
                    tokensToRemove.add(tokenA)
            }
        }
    }

    // The actual token removal.
    if (tokensToRemove.isNotEmpty())
        tokens.removeAll(tokensToRemove)

    return tokens
}
