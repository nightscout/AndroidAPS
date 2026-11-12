package app.aaps.plugins.dexcomoneplus.gatt

/**
 * GATT connect / discovery / notify routing.
 *
 * ⚠️ ASYNC IMPACT: `BluetoothGattCallback` arrives on the binder thread.
 * Sequential work (MTU, discover, CCCD, write) must run on a single bleExecutor.
 * Platform impl must serialize GATT ops (one outstanding write/descriptor) — Android 13+
 * returns status 201 (`ERROR_GATT_WRITE_REQUEST_BUSY`) otherwise.
 * [awaitNotify] / [awaitControlNotify] / [awaitBackfillNotify] block the caller —
 * Auth/ExtraData, Control, and ProbablyBackfill use **separate** queues.
 * [disconnect] must unblock all three via an identity disconnect sentinel (not any empty
 * byte array). Do not block that executor with network or dosing work.
 */
interface OnePlusGattClient {
    /**
     * Connect + discover + Auth/Extra CCCD until ready.
     * @param autoConnect Ob1 CONNECT path — true after hard-connect failures on flaky stacks
     */
    fun connect(deviceAddress: String, autoConnect: Boolean = false)
    fun disconnect()
    fun isConnected(): Boolean

    fun writeAuthentication(payload: ByteArray?)
    fun writeExtraData(payload: ByteArray?)
    fun writeControl(payload: ByteArray?)

    /**
     * Enable Control CCCD for the EGV path.
     * Prefer **notifications** (Juggluco Dex path); fall back to indications (Ob1) if the write fails.
     */
    fun enableControlNotifications()

    /**
     * Force Control **indications** (Ob1). Used when NOTIFY CCCD succeeded but no Control
     * traffic arrives (silent firmware/stack mismatch).
     */
    fun enableControlIndications()

    /**
     * Enable notifications on ProbablyBackfill (Ob1 `setupNotification(ProbablyBackfill)`).
     */
    fun enableBackfillNotifications()

    /** True when the remote device is Android-bonded (`BluetoothDevice.BOND_BONDED`). */
    fun isBonded(): Boolean

    /**
     * Request Android bonding (system pairing UI may appear). No-op if already bonded.
     * Registers a bond-state receiver; pair with [awaitBondComplete].
     * @return false if createBond could not be started
     */
    fun createBond(): Boolean

    /**
     * Drop the Android OS bond via hidden [BluetoothDevice.removeBond] (Juggluco-style).
     * Used when KEKS AuthStatus rejects (`authenticated != 1`) while the phone is still bonded —
     * otherwise the next reconnect short-auths / stalls with sensor `bonded=1`.
     *
     * ⚠️ ASYNC IMPACT: may drop the GATT link; caller should treat as reconnect trigger.
     * @return true if already unbonded or removeBond returned true
     */
    fun removeBond(): Boolean

    /**
     * Block until [BluetoothDevice.BOND_BONDED] (BroadcastReceiver), Juggluco-style:
     * tear down Auth/Extra CCCDs while [BluetoothDevice.BOND_BONDING], restore after bonded.
     *
     * ⚠️ ASYNC IMPACT: blocks bleExecutor; receiver runs on main/binder and must not
     * serialize against a concurrent GATT write from this thread.
     */
    fun awaitBondComplete(timeoutMs: Long): Boolean

    /**
     * Block until next Auth or ExtraData notification (KEKS path), with source tag.
     * Prefer this over [awaitNotify] so KEKS can route Ob1-style.
     */
    fun awaitKeksNotify(timeoutMs: Long): OnePlusKeksNotify?

    /**
     * Block until next Auth or ExtraData notification (KEKS path).
     * Payload only — prefer [awaitKeksNotify] for handshake.
     */
    fun awaitNotify(timeoutMs: Long): ByteArray?

    /**
     * Block until next Control characteristic indication/notification (EGV path).
     * Separate from [awaitKeksNotify] so KEKS and EGV do not cross-consume packets.
     */
    fun awaitControlNotify(timeoutMs: Long): ByteArray?

    /**
     * Block until next ProbablyBackfill notification (history stream).
     */
    fun awaitBackfillNotify(timeoutMs: Long): ByteArray?
}
