package app.aaps.core.objects.workflow

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dev.zacsweers.metro.MapKey
import kotlin.reflect.KClass

/**
 * What a worker wired by Metro contributes so the app's `WorkerFactory` can build it.
 * The two arguments are the ones only WorkManager has, which makes every worker an assisted-injection
 * case: graph dependencies plus a caller-supplied [Context] and [WorkerParameters]. Metro generates the
 * implementation from `@AssistedFactory`, so a worker only declares a nested factory extending this.
 * It lives beside [LoggingWorker] rather than in `:app` because workers live in many modules and none
 * of them can depend on `:app`.
 */
fun interface MetroWorkerCreator {

    fun create(context: Context, params: WorkerParameters): ListenableWorker
}

/**
 * What a worker's nested `@AssistedFactory` extends, so it does not have to restate `create`.
 * The shape Metro's own Android sample uses. Naming the worker as the type argument is what makes the
 * return type right by construction - with a bare [MetroWorkerCreator] each factory repeats the
 * signature and could declare the wrong worker type, which still compiles because the map only asks for
 * a [ListenableWorker].
 * ```kotlin
 * @AssistedFactory
 * abstract class Factory : WorkerInstanceFactory<LoadBgWorker>()
 * ```
 */
abstract class WorkerInstanceFactory<T : ListenableWorker> : MetroWorkerCreator {

    abstract override fun create(context: Context, params: WorkerParameters): T
}

/**
 * The map key for the worker multibinding.
 * A key of its own rather than Metro's general `@ClassKey`, because `@ClassKey` produces a
 * `KClass<*>`: the map has to be declared as `Map<KClass<*>, MetroWorkerCreator>` and any class at all
 * type-checks as a key. This one only accepts a [ListenableWorker], so keying a worker binding with the
 * wrong class is a compile error instead of a lookup that never matches - and a lookup that never
 * matches means WorkManager quietly falls back, which is the failure mode this whole seam exists to
 * remove. Metro's own Android sample defines the same key for the same reason.
 */
@MapKey
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class WorkerKey(val value: KClass<out ListenableWorker>)
