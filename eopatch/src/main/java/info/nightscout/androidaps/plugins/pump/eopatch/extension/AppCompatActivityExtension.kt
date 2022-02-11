package info.nightscout.androidaps.plugins.pump.eopatch.extension

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import java.io.Serializable

fun AppCompatActivity.replaceFragmentInActivity(fragment: Fragment, frameId: Int, addToBackStack: Boolean = false) {
    supportFragmentManager.transact {
        replace(frameId, fragment)
        if (addToBackStack) addToBackStack(null)
    }
}

private inline fun FragmentManager.transact(action: FragmentTransaction.() -> Unit) {
    beginTransaction().apply {
        action()
    }.commit()
}

fun Intent.fillExtras(params: Array<out Pair<String, Any?>>){
    fillIntentArguments(this, params)
}

private fun fillIntentArguments(intent: Intent, params: Array<out Pair<String, Any?>>) {
    params.forEach {
        when (val value = it.second) {
            null            -> intent.putExtra(it.first, null as Serializable?)
            is Int          -> intent.putExtra(it.first, value)
            is Long         -> intent.putExtra(it.first, value)
            is CharSequence -> intent.putExtra(it.first, value)
            is String       -> intent.putExtra(it.first, value)
            is Float        -> intent.putExtra(it.first, value)
            is Double       -> intent.putExtra(it.first, value)
            is Char         -> intent.putExtra(it.first, value)
            is Short -> intent.putExtra(it.first, value)
            is Boolean      -> intent.putExtra(it.first, value)
            is Serializable -> intent.putExtra(it.first, value)
            is Bundle       -> intent.putExtra(it.first, value)
            is Parcelable   -> intent.putExtra(it.first, value)
            is Array<*>     -> when {
                value.isArrayOf<CharSequence>() -> intent.putExtra(it.first, value)
                value.isArrayOf<String>()       -> intent.putExtra(it.first, value)
                value.isArrayOf<Parcelable>()   -> intent.putExtra(it.first, value)
                else                            -> throw Exception("Intent extra ${it.first} has wrong type ${value.javaClass.name}")
            }
            is IntArray -> intent.putExtra(it.first, value)
            is LongArray -> intent.putExtra(it.first, value)
            is FloatArray -> intent.putExtra(it.first, value)
            is DoubleArray -> intent.putExtra(it.first, value)
            is CharArray -> intent.putExtra(it.first, value)
            is ShortArray -> intent.putExtra(it.first, value)
            is BooleanArray -> intent.putExtra(it.first, value)
            else -> throw Exception("Intent extra ${it.first} has wrong type ${value.javaClass.name}")
        }
        return@forEach
    }
}