package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.interfaces.DBEntry

/**
 * A DAO that adds updated or inserted entries to a list
 */
internal abstract class DelegatedDao(protected val changes: MutableList<DBEntry>)