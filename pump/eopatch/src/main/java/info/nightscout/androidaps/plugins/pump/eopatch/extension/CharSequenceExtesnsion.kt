package info.nightscout.androidaps.plugins.pump.eopatch.extension

import android.text.Spanned

fun CharSequence?.check(oldText: CharSequence?): Boolean {
    val text = this
    if (text === oldText || text == null && oldText?.length == 0) {
        return false
    }
    if (text is Spanned) {
        if (text == oldText) {
            return false // No change in the spans, so don't set anything.
        }
    } else if (!text.haveContentsChanged(oldText)) {
        return false  // No content changes, so don't set anything.
    }
    return true
}

fun CharSequence?.haveContentsChanged(str2: CharSequence?): Boolean {
    val str1: CharSequence? = this
    if (str1 == null != (str2 == null)) {
        return true
    } else if (str1 == null) {
        return false
    }
    val length = str1.length
    if (length != str2!!.length) {
        return true
    }
    for (i in 0 until length) {
        if (str1[i] != str2[i]) {
            return true
        }
    }
    return false
}