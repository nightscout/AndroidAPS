package app.aaps.ui.search

import java.text.Normalizer

/** Splits each character into base plus accent (NFD), then drops the accents (Unicode mark category). */
internal actual fun String.removeDiacritics(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")
