package app.aaps.database.impl.transactions

import app.aaps.database.entities.VersionChange

class VersionChangeTransaction(
    private val versionName: String,
    private val versionCode: Int,
    private val gitRemote: String?,
    private val commitHash: String?
) : Transaction<Unit>() {

    override fun run() {
        val current = database.versionChangeDao.getMostRecentVersionChange()
        if (current == null
            || current.versionName != versionName
            || current.versionCode != versionCode
            || current.gitRemote != gitRemote
            || current.commitHash != commitHash
        ) {
            database.versionChangeDao.insert(
                VersionChange(
                    timestamp = System.currentTimeMillis(),
                    versionCode = versionCode,
                    versionName = versionName,
                    gitRemote = gitRemote,
                    commitHash = commitHash
                )
            )
        }
    }

}