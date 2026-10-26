package app.aaps.shared.impl.sharedPreferences

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.FileStorage
import androidx.datastore.core.Serializer
import app.aaps.core.interfaces.sharedPreferences.StoreCipher
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** [StoreCodec] as a DataStore serializer. No file reads as an empty store; that is DataStore's rule. */
internal class StoreFileSerializer(private val cipher: StoreCipher) : Serializer<Map<String, Any>> {

    override val defaultValue: Map<String, Any> = emptyMap()

    override suspend fun readFrom(input: InputStream): Map<String, Any> = StoreCodec.decode(input.readBytes(), cipher)

    override suspend fun writeTo(t: Map<String, Any>, output: OutputStream) = output.write(StoreCodec.encode(t, cipher))
}

/**
 * Opens the encrypted store kept in [file], running [migrations] first if they apply.
 *
 * DataStore's own file storage: it writes a sibling file, syncs it and moves it over the real one, so
 * a crash leaves the old content or the new, never half of it. There is deliberately no corruption
 * handler - DataStore's would replace an unreadable file with an empty one.
 *
 * The store and its DataStore live in [scope], which the caller owns. DataStore refuses a second
 * instance on a file while the first is alive, so to open the same file again - after a failed open,
 * when the user chose to start fresh - the caller cancels [scope] and waits for it to finish first.
 */
fun openEncryptedStore(
    file: File,
    cipher: StoreCipher,
    migrations: List<DataMigration<Map<String, Any>>>,
    scope: CoroutineScope,
    onSaveFailure: (Throwable) -> Unit
): EncryptedKeyValueStore {
    val dataStore = DataStoreFactory.create(storage = FileStorage(StoreFileSerializer(cipher)) { file }, migrations = migrations, scope = scope)
    return EncryptedKeyValueStore.open(dataStore, scope, onSaveFailure)
}
