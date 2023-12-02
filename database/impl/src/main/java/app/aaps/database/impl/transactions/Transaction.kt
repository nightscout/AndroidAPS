package app.aaps.database.impl.transactions

import app.aaps.database.impl.DelegatedAppDatabase

/**
 * Base class for database transactions
 * @param T The return type of the Transaction
 */
abstract class Transaction<T> {

    /**
     * Executes the Transaction
     */
    internal abstract fun run(): T

    internal lateinit var database: DelegatedAppDatabase

}