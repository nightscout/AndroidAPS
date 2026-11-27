package app.aaps.workflow

import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A [FabricPrivacy] that keeps what is sent to Crashlytics, in the order it was sent.
 *
 * The executor reports from its own threads, so the list is kept as immutable copies behind one atomic
 * reference.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class RecordingFabricPrivacy : FabricPrivacy {

    private val items = AtomicReference(emptyList<Any>())

    /** What was sent, in order: a String for a line of the Crashlytics log, the Throwable for an error. */
    val sent: List<Any> get() = items.load()

    val exceptions: List<Throwable> get() = sent.filterIsInstance<Throwable>()

    private fun add(item: Any) {
        while (true) {
            val old = items.load()
            if (items.compareAndSet(old, old + item)) return
        }
    }

    override fun logMessage(message: String) = add(message)
    override fun logException(throwable: Throwable) = add(throwable)
    override fun setUserProperty(key: String, value: String) = Unit
    override fun logCustom(event: String) = Unit
    override fun logCustom(name: String, params: Map<String, Long>) = Unit
    override fun fabricEnabled(): Boolean = true
    override fun logWearException(wearException: EventData.WearException) = Unit
}
