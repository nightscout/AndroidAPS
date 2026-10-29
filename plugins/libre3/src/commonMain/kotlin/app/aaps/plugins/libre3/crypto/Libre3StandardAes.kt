package app.aaps.plugins.libre3.crypto

/**
 * One ordinary AES-128 block maker, built from [key] by the platform this driver runs on.
 *
 * This is the only part of the glucose data plane that is platform specific. The CCM rule around
 * it - the nonce, the counter, the tag - is shared code, so platforms cannot drift on
 * anything but the block itself, and a block is a fixed answer that golden vectors can check.
 *
 * It must be plain AES with no padding and no chaining: 16 bytes in, 16 bytes out, the same answer
 * every time. The sensor's own table driven block maker is a different thing and lives in
 * `Libre3LibAes`; the two planes must never be mixed.
 *
 * @param key 16 bytes. The caller has already checked the size.
 */
internal expect fun libre3StandardAesBlock(key: ByteArray): Libre3AesBlock
