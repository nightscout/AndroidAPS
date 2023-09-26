package app.aaps.core.ui.elements

import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.NumberKeyListener

internal class DigitsKeyListenerWithComma @JvmOverloads constructor(private val sign: Boolean = false, private val decimal: Boolean = false) : NumberKeyListener() {

    private var accepted: CharArray
    override fun getAcceptedChars(): CharArray = accepted
    /**
     * Allocates a DigitsKeyListener that accepts the digits 0 through 9,
     * plus the minus sign (only at the beginning) and/or decimal point
     * (only one per field) if specified.
     */
    /**
     * Allocates a DigitsKeyListener that accepts the digits 0 through 9.
     */
    init {
        val kind = (if (sign) SIGN else 0) or if (decimal) DECIMAL else 0
        accepted = CHARACTERS[kind]
    }

    override fun getInputType(): Int {
        var contentType = InputType.TYPE_CLASS_NUMBER
        if (sign) {
            contentType = contentType or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        if (decimal) {
            contentType = contentType or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        return contentType
    }

    override fun filter(
        source: CharSequence, start: Int, end: Int,
        dest: Spanned, dstart: Int, dend: Int
    ): CharSequence? {
        var sourceSequence = source
        var startIndex = start
        var endIndex = end
        val out = super.filter(sourceSequence, startIndex, endIndex, dest, dstart, dend)
        if (!sign && !decimal) return out
        if (out != null) {
            sourceSequence = out
            startIndex = 0
            endIndex = out.length
        }
        var sign = -1
        var decimal = -1
        val dLen = dest.length

        /*
         * Find out if the existing text has '-' or '.' characters.
         */
        for (i in 0 until dstart) {
            val c = dest[i]
            if (c == '-') sign = i
            else if (c == '.' || c == ',') decimal = i
        }
        for (i in dend until dLen) {
            val c = dest[i]
            if (c == '-') return "" // Nothing can be inserted in front of a '-'.
            else if (c == '.' || c == ',') decimal = i
        }

        /*
         * If it does, we must strip them out from the source.
         * In addition, '-' must be the very first character,
         * and nothing can be inserted before an existing '-'.
         * Go in reverse order so the offsets are stable.
         */
        var stripped: SpannableStringBuilder? = null
        for (i in endIndex - 1 downTo startIndex) {
            val c = sourceSequence[i]
            var strip = false
            if (c == '-') {
                if (i != startIndex || dstart != 0) strip = true
                else if (sign >= 0) strip = true
                else sign = i
            } else if (c == '.' || c == ',') {
                if (decimal >= 0) strip = true
                else decimal = i
            }
            if (strip) {
                if (endIndex == startIndex + 1) return "" // Only one character, and it was stripped.
                if (stripped == null) stripped = SpannableStringBuilder(sourceSequence, startIndex, endIndex)
                stripped.delete(i - startIndex, i + 1 - startIndex)
            }
        }
        return stripped ?: out
    }

    companion object {

        /**
         * The characters that are used.
         *
         * @see android.view.KeyEvent.getMatch
         *
         * @see .getAcceptedChars
         */
        private val CHARACTERS =
            arrayOf(
                charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9'),
                charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-'),
                charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ','),
                charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '.', ',')
            )
        private const val SIGN = 1
        private const val DECIMAL = 2
        private val sInstance = arrayOfNulls<DigitsKeyListenerWithComma>(4)
        val instance: DigitsKeyListenerWithComma?
            /**
             * Returns a DigitsKeyListener that accepts the digits 0 through 9.
             */
            get() = getInstance(sign = false, decimal = false)

        /**
         * Returns a DigitsKeyListener that accepts the digits 0 through 9,
         * plus the minus sign (only at the beginning) and/or decimal point
         * (only one per field) if specified.
         */
        fun getInstance(sign: Boolean, decimal: Boolean): DigitsKeyListenerWithComma? {
            val kind = (if (sign) SIGN else 0) or if (decimal) DECIMAL else 0
            if (sInstance[kind] != null) return sInstance[kind]
            sInstance[kind] = DigitsKeyListenerWithComma(sign, decimal)
            return sInstance[kind]
        }

        /**
         * Returns a DigitsKeyListener that accepts only the characters
         * that appear in the specified String.  Note that not all characters
         * may be available on every keyboard.
         */
        fun getInstance(accepted: String): DigitsKeyListenerWithComma {
            // TODO: do we need a cache of these to avoid allocating?
            val dim = DigitsKeyListenerWithComma()
            dim.accepted = CharArray(accepted.length)
            accepted.toCharArray(dim.accepted, 0, 0, accepted.length)
            return dim
        }
    }
}