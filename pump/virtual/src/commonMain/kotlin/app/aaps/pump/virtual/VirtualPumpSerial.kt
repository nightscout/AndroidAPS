package app.aaps.pump.virtual

import kotlin.random.Random

/**
 * The serial number the virtual pump reports.
 *
 * There is no hardware to ask, so this is a generated per-install code instead. It is created once and
 * kept in preferences, because `pumpSync` records it on every entry and a value that changed on each
 * start would scatter one pump's history across many serials.
 *
 * ## Why it is generated rather than taken from the platform
 *
 * A platform id - the Firebase installation id, or `identifierForVendor` on iOS - is far too long to
 * read out or type, which is what this is for: a user quoting their install in a support thread. On
 * Android such an id also arrives asynchronously, so an early read returns "". Shortening one does not
 * help either: those ids are long *because* uniqueness costs length, so a truncation is no better than
 * the random code below.
 *
 * ## Uniqueness
 *
 * [LENGTH] symbols from a [ALPHABET]-symbol alphabet is 32^10, about 1.1e15 codes. Across a million
 * installs the chance that any two collide is roughly 0.04%, and every reinstall draws again, so the
 * population only grows. This is a probability, not a guarantee - no code short enough to type can give
 * one - but at this size a collision is not worth designing against.
 *
 * The alphabet is Crockford's Base32: the digits and letters minus `I`, `L`, `O` and `U`. Nothing in it
 * can be confused with `0` or `1` when read aloud or typed, and no word can form by accident.
 */
private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
private const val LENGTH = 10

/**
 * Draws a new serial. Not a secret - it identifies an install, it does not protect anything - so the
 * default random source is enough, and it is multiplatform, which the platform ids were not.
 */
internal fun generateVirtualPumpSerial(random: Random = Random.Default): String =
    buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
