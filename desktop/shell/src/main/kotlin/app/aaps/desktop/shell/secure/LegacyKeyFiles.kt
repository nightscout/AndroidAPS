package app.aaps.desktop.shell.secure

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.hexStringToByteArray
import java.io.File

/**
 * Moves the old plain key files (`~/.aaps/keys/<alias>`, the key as hex) into an OS vault.
 *
 * Each key is stored, read back and compared before its file is deleted, so a key store that accepts a
 * write without keeping it cannot destroy the only copy. A key the vault already holds with a different
 * value is a conflict that only a human can sort out: the file is kept and the case is logged.
 *
 * Runs on every start and does nothing once the folder holds no plain key files. Nothing is moved when
 * the vault is [FileKeyVault] - those files are its keys.
 */
object LegacyKeyFiles {

    fun migrate(keysDir: File, vault: KeyVault, log: AAPSLogger) {
        if (!vault.isOsBacked) return
        val files = keysDir.listFiles { file -> file.isFile && file.name.none { it == '.' } }.orEmpty()
        for (file in files) {
            val alias = file.name
            try {
                val key = file.readText().trim().hexStringToByteArray()
                val held = vault.load(alias)
                when {
                    held == null                -> {
                        vault.store(alias, key)
                        if (key.contentEquals(vault.load(alias))) deleteMoved(file, alias, vault, log)
                        else log.error(LTag.CORE, "Key $alias did not read back from ${vault.name}; the key file is kept")
                    }

                    held.contentEquals(key)     -> deleteMoved(file, alias, vault, log)
                    else                        -> log.error(LTag.CORE, "Key $alias in ${vault.name} differs from ${file.path}; the key file is kept")
                }
            } catch (e: Exception) {
                log.error(LTag.CORE, "Key $alias could not be moved into ${vault.name}; the key file is kept: ${e.message}")
            }
        }
    }

    private fun deleteMoved(file: File, alias: String, vault: KeyVault, log: AAPSLogger) {
        if (file.delete()) log.info(LTag.CORE, "Key $alias moved into ${vault.name}")
        else log.error(LTag.CORE, "Key $alias is in ${vault.name}, but ${file.path} could not be deleted")
    }
}
