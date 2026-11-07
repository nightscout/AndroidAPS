package app.aaps.shared.impl.sharedPreferences

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import app.aaps.core.interfaces.sharedPreferences.StoreCipher
import kotlinx.coroutines.CoroutineScope
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath

/** [StoreCodec] as a DataStore serializer. No file reads as an empty store; that is DataStore's rule. */
internal class StoreFileSerializer(private val cipher: StoreCipher) : OkioSerializer<Map<String, Any>> {

    override val defaultValue: Map<String, Any> = emptyMap()

    override suspend fun readFrom(source: BufferedSource): Map<String, Any> = StoreCodec.decode(source.readByteArray(), cipher)

    override suspend fun writeTo(t: Map<String, Any>, sink: BufferedSink) {
        sink.write(StoreCodec.encode(t, cipher))
    }
}

/**
 * Opens the encrypted store kept in the file at [path] (absolute), running [migrations] first if they
 * apply.
 *
 * DataStore's okio file storage, the same on every platform: it is the only one DataStore has on iOS.
 * It writes a sibling `.tmp` file, syncs it and moves it over the real one, so a crash leaves the old
 * content or the new, never half of it. There is deliberately no corruption handler - DataStore's would
 * replace an unreadable file with an empty one.
 *
 * The store and its DataStore live in [scope], which the caller owns. DataStore refuses a second
 * instance on a file while the first is alive, so to open the same file again - after a failed open,
 * when the user chose to start fresh - the caller cancels [scope] and waits for it to finish first.
 */
fun openEncryptedStore(
    path: String,
    cipher: StoreCipher,
    migrations: List<DataMigration<Map<String, Any>>>,
    scope: CoroutineScope,
    onSaveFailure: (Throwable) -> Unit
): EncryptedKeyValueStore {
    val storage = OkioStorage(FileSystem.SYSTEM, StoreFileSerializer(cipher)) { path.toPath() }
    val dataStore = DataStoreFactory.create(storage = storage, migrations = migrations, scope = scope)
    return EncryptedKeyValueStore.open(dataStore, scope, onSaveFailure)
}

/**
 * The settings file at [path] exists, but cannot be opened with the key this device has. Starting fresh
 * is the way out. The start code of each platform turns the `CorruptionException` of a failed open into
 * this, so its start screen can offer that.
 */
class StoreUnreadableException(val path: String, cause: Throwable) :
    Exception("The settings in $path cannot be read: ${cause.message}", cause)
