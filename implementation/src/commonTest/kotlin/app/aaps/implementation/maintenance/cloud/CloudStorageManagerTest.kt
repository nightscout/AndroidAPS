package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.CloudFile
import app.aaps.core.interfaces.maintenance.CloudFileListResult
import app.aaps.core.interfaces.maintenance.CloudFolder
import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.implementation.maintenance.formats.FakeKeyValueStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which cloud, if any, the export goes to.
 *
 * Small, and worth having anyway: this is the class every export and import asks before doing
 * anything, and the answers it gives are the difference between a backup reaching someone's Drive
 * and quietly staying on the phone. The provider it hands back is exercised next door; what is
 * checked here is the choosing.
 */
class CloudStorageManagerTest {

    private val store = FakeKeyValueStore()
    private val drive = FakeProvider(StorageTypes.GOOGLE_DRIVE, signedIn = true)

    private fun manager(vararg providers: CloudStorageProvider) =
        CloudStorageManager(SilentLogger(), store, providers.toSet())

    /** Nothing chosen means local, and local means no provider - not a provider that does nothing. */
    @Test
    fun `with nothing chosen there is no cloud`() {
        val sut = manager(drive)

        assertNull(sut.getActiveProvider())
        assertFalse(sut.isCloudStorageActive())
    }

    @Test
    fun `choosing a provider makes it the active one`() {
        val sut = manager(drive)

        sut.setActiveStorageType(StorageTypes.GOOGLE_DRIVE)

        assertEquals(drive, sut.getActiveProvider())
        assertTrue(sut.isCloudStorageActive())
    }

    /** A stored choice naming something that is not installed must not become a crash on a screen. */
    @Test
    fun `a choice with no matching provider is no cloud`() {
        val sut = manager(drive)

        sut.setActiveStorageType("dropbox")

        assertNull(sut.getActiveProvider())
    }

    @Test
    fun `going back to local drops the provider`() {
        val sut = manager(drive)
        sut.setActiveStorageType(StorageTypes.GOOGLE_DRIVE)

        sut.setActiveStorageType(StorageTypes.LOCAL)

        assertNull(sut.getActiveProvider())
        assertFalse(sut.isCloudStorageActive())
    }

    /** The export screen asks this to decide whether to offer a cloud at all. */
    @Test
    fun `credentials are reported only when a provider has them`() {
        assertTrue(manager(drive).hasAnyCloudCredentials())
        assertFalse(manager(FakeProvider(StorageTypes.GOOGLE_DRIVE, signedIn = false)).hasAnyCloudCredentials())
    }

    @Test
    fun `a provider that is not signed in is not offered as authenticated`() {
        val sut = manager(FakeProvider(StorageTypes.GOOGLE_DRIVE, signedIn = false))

        assertTrue(sut.getAuthenticatedProviders().isEmpty())
        assertEquals(1, sut.getAvailableProviders().size)
    }

    /** Signing out has to clear the choice too, or the screen keeps offering a cloud that is gone. */
    @Test
    fun `clearing credentials clears them on the provider`() {
        val sut = manager(drive)
        sut.setActiveStorageType(StorageTypes.GOOGLE_DRIVE)

        sut.clearAllCredentials()

        assertTrue(drive.cleared)
    }

    private class FakeProvider(
        override val storageType: String,
        private val signedIn: Boolean
    ) : CloudStorageProvider {

        var cleared = false

        override val displayName: String = "Fake"
        override val icon: ImageVector = Icons.Default.Cloud
        override val authorizedText: TextRef = TextRef.Literal("authorized")
        override val reAuthRequiredText: TextRef = TextRef.Literal("sign in again")

        override suspend fun startAuth(): String? = null
        override suspend fun completeAuth(authCode: String): Boolean = false
        override fun hasValidCredentials(): Boolean = signedIn
        override fun clearCredentials() { cleared = true }
        override suspend fun revokeAccess(): Boolean = true
        override suspend fun getValidAccessToken(): String? = null
        override suspend fun testConnection(): Boolean = signedIn
        override fun hasConnectionError(): Boolean = false
        override fun clearConnectionError() = Unit
        override suspend fun getOrCreateFolderPath(path: String): String? = "folder"
        override suspend fun createFolder(name: String, parentId: String): String? = "folder"
        override suspend fun listFolders(parentId: String): List<CloudFolder> = emptyList()
        override suspend fun uploadFileToPath(fileName: String, content: ByteArray, mimeType: String, path: String): String? = "id"
        override suspend fun uploadFile(fileName: String, content: ByteArray, mimeType: String): String? = "id"
        override suspend fun downloadFile(fileId: String): ByteArray? = null
        override suspend fun listSettingsFiles(pageSize: Int, pageToken: String?): CloudFileListResult =
            CloudFileListResult(files = emptyList<CloudFile>())

        override fun getSelectedFolderId(): String = "root"
        override fun setSelectedFolderId(folderId: String) = Unit
    }

    private class SilentLogger : AAPSLogger {
        override fun debug(tag: LTag, message: String) {}
        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }
}
