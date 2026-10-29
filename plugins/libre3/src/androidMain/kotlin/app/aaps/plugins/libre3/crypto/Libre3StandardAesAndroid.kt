package app.aaps.plugins.libre3.crypto

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Android block maker, the same `javax.crypto` call the driver has always made.
 *
 * A fresh `Cipher` per block is kept on purpose: that is what the code did before this moved to
 * shared code, and a block maker that reused one object would change when it throws.
 */
internal actual fun libre3StandardAesBlock(key: ByteArray): Libre3AesBlock {
    val keySpec = SecretKeySpec(key, "AES")
    return Libre3AesBlock { input ->
        require(input.size == AES_BLOCK_SIZE) { "an AES block is 16 bytes" }
        // No padding, one block at a time: this is the raw block maker that CCM needs.
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        cipher.doFinal(input)
    }
}

/** 16 bytes, the size of one AES block. */
private const val AES_BLOCK_SIZE = 16
