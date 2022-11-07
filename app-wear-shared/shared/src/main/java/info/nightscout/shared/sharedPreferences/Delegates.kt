package info.nightscout.shared.sharedPreferences

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/*

These classes allow to combine SP values with Kotlin delegates like this:

    private var myValue: String
        by SPDelegateString(sp, "myValueKey", "default-value")

Then, accessing myValue works by simply using the Kotlin setters & getters:

    val value = myValue // reading from sp
    myValue = "newvalue" // writing to sp

*/

class SPDelegateBoolean(
    private val sp: SP,
    private val key: String,
    private val defaultValue: Boolean = false,
    private val commit: Boolean = false
) : ReadWriteProperty<Any, Boolean> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sp.getBoolean(key, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) =
        sp.edit(commit = commit) { putBoolean(key, value) }
}

class SPDelegateDouble(
    private val sp: SP,
    private val key: String,
    private val defaultValue: Double = 0.0,
    private val commit: Boolean = false
) : ReadWriteProperty<Any, Double> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sp.getDouble(key, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Double) =
        sp.edit(commit = commit) { putDouble(key, value) }
}

class SPDelegateLong(
    private val sp: SP,
    private val key: String,
    private val defaultValue: Long = 0,
    private val commit: Boolean = false
) : ReadWriteProperty<Any, Long> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sp.getLong(key, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) =
        sp.edit(commit = commit) { putLong(key, value) }
}

class SPDelegateInt(
    private val sp: SP,
    private val key: String,
    private val defaultValue: Int = 0,
    private val commit: Boolean = false
) : ReadWriteProperty<Any, Int> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sp.getInt(key, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) =
        sp.edit(commit = commit) { putInt(key, value) }
}

class SPDelegateString(
    private val sp: SP,
    private val key: String,
    private val defaultValue: String = "",
    private val commit: Boolean = false
) : ReadWriteProperty<Any, String> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        sp.getString(key, defaultValue)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: String) =
        sp.edit(commit = commit) { putString(key, value) }
}
