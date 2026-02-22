package com.nightscout.eversense.util

import android.content.SharedPreferences
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.edit
import com.nightscout.eversense.models.EversenseSecureState
import com.nightscout.eversense.packets.e365.utils.toByteArray
import com.nightscout.eversense.packets.e365.utils.toLong
import kotlinx.serialization.json.Json
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.CCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.KeyAgreement
import kotlin.collections.toByteArray

class EversenseCrypto365Util(val preference: SharedPreferences) {
    private var ephemPrivate: ECPrivateKey? = null
    private var ephemPublic: ECPublicKey? = null
    private var ephemSalt: ByteArray? = null

    private var messageSequenceNumber = 1
    private var sessionKey: ByteArray? = null

    fun canUseShortcut(): Boolean {
        return getState(preference).canUseShortcut
    }

    fun allowUseShortcut() {
        val state = getState(preference)
        state.canUseShortcut = true
        saveState(state, preference)
    }

    fun disallowUseShortcut() {
        val state = getState(preference)
        state.canUseShortcut = false
        saveState(state, preference)
    }

    fun getClientPublicKey(): ByteArray {
        val state = getState(preference)
        return state.publicKey.hexToByteArray()
    }

    fun generateKeyPairIfNotExists(): Boolean {
        val state = getState(preference)
        if (state.clientId.isNotEmpty() && state.publicKey.isNotEmpty() && state.privateKey.isNotEmpty()) {
            EversenseLogger.debug(TAG, "Already generated keypair")
            return true
        }

        val keyPair = generatePrivateKeyPair() ?: return false
        state.clientId = generateRandomBytes(32).toHexString()
        state.privateKey = keyPair.private.encoded.toHexString()
        state.publicKey = keyPair.public.encoded.toHexString()

        saveState(state, preference)
        EversenseLogger.debug(TAG, "Generated keypair!")
        return true
    }

    fun getClientId(): ByteArray {
        val state = getState(preference)
        return state.clientId.hexToByteArray()
    }

    fun getStartSecret(signature: ByteArray): ByteArray {
        val public = ephemPublic?.encoded ?: return byteArrayOf()
        val salt = ephemSalt ?: return byteArrayOf()

        var secret = byteArrayOf(128.toByte(), 0)
        secret += getState(preference).clientId.hexToByteArray()
        secret += public.copyOfRange(27, public.count())
        secret += salt
        secret += signature

        return secret
    }

    fun generateEphem(): ByteArray? {
        val keyPair = generatePrivateKeyPair() ?:run {
            EversenseLogger.error(TAG, "Failed to generate keypair...")
            return null
        }

        val privateKey = getState(preference).privateKey.hexToByteArray()
        try {
            val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKey))
            val publicKey = keyPair.public.encoded
            val v2Salt = generateRandomBytes(8)

            ephemPrivate = keyPair.private as ECPrivateKey
            ephemPublic = keyPair.public as ECPublicKey
            ephemSalt = v2Salt

            val data = publicKey.copyOfRange(27, publicKey.count()) + v2Salt
            return ecdsaSign(privateKey, data)
        } catch (e: Exception) {
            e.printStackTrace()
            EversenseLogger.error(TAG, "Got exception during generateEphem - exception: $e")
            return null
        }
    }

    fun generateSessionKey(encodedPublicKey: ByteArray) {
        try {
            val salt = ephemSalt ?: return

            val ecPoint = ECPoint(
                BigInteger(1, encodedPublicKey.copyOfRange(0, 32)),
                BigInteger(1, encodedPublicKey.copyOfRange(32, 64))
            )
            val algorithmParameters = AlgorithmParameters.getInstance(KeyProperties.KEY_ALGORITHM_EC).run {
                init(ECGenParameterSpec("secp256r1"))
                getParameterSpec(ECParameterSpec::class.java)
            }

            val publicKey = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(
                ECPublicKeySpec(ecPoint, algorithmParameters)
            )

            val sharedSecret =
                KeyAgreement.getInstance("ECDH").run {
                    init(ephemPrivate)
                    doPhase(publicKey, true)
                    generateSecret()
                }

            val symmetricKey = HKDFBytesGenerator(SHA256Digest()).run {
                init(HKDFParameters(sharedSecret, null, salt))

                val arr = ByteArray(16)
                generateBytes(arr, 0, 16)
                arr
            }

            EversenseLogger.info(TAG, "SessionKey = ${symmetricKey.toHexString()}")
            sessionKey = symmetricKey
        } catch (e: Exception) {
            e.printStackTrace()
            EversenseLogger.error(TAG, "Failed to generate sessionKey: $e")
        }
    }

    fun encrypt(data: ByteArray): ByteArray {
        val ephemSalt = ephemSalt ?:run {
            EversenseLogger.error(TAG, "No salt available...")
            return byteArrayOf()
        }

        val sessionKey = sessionKey ?:run {
            EversenseLogger.error(TAG, "No sessionKey available...")
            return byteArrayOf()
        }

        val i = (messageSequenceNumber and 0x3FFF).toLong()
        val prefix = (i shl 2).toShort().toByteArray()

        val salt = generateEncryptionSalt(ephemSalt, i)
        messageSequenceNumber++

        val encryptedData = aeadCCM(salt, data, prefix, true, sessionKey) ?: return byteArrayOf()
        return ByteBuffer.allocate(encryptedData.count() + 2).run {
            put(prefix)
            put(encryptedData)
            array()
        }
    }

    fun decrypt(response: ByteArray): ByteArray {
        val ephemSalt = ephemSalt ?:run {
            EversenseLogger.error(TAG, "No salt available...")
            return byteArrayOf()
        }

        val sessionKey = sessionKey ?:run {
            EversenseLogger.error(TAG, "No sessionKey available...")
            return byteArrayOf()
        }

        val cypherText = response.copyOfRange(2, response.size)
        val prefix = response.copyOfRange(0, 2)
        val i = (prefix.toLong() shr 2) and 0x3FFF
        val salt = generateEncryptionSalt(ephemSalt, i)

        return aeadCCM(salt, cypherText, prefix, false, sessionKey) ?: byteArrayOf()
    }

    companion object {
        private const val TAG = "EversenseCrypto365Handler"
        private val JSON = Json { ignoreUnknownKeys = true }

        private fun generateRandomBytes(i: Int): ByteArray {
            val bArr = ByteArray(i)
            SecureRandom().nextBytes(bArr)
            return bArr
        }

        private fun generatePrivateKeyPair(): KeyPair? {
            try {
                return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC).run {
                    initialize(ECGenParameterSpec("secp256r1"))
                    generateKeyPair()
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "Error generating key pair: $e")
                return null
            }
        }

        private fun ecdsaSign(privateKey: PrivateKey, data: ByteArray): ByteArray? {
            try {
                val signature = Signature.getInstance("SHA256withECDSA").run {
                    initSign(privateKey)
                    update(data)
                    sign()
                }

                val dLSequence = ASN1InputStream(signature).readObject() as DLSequence
                val value1 = (dLSequence.getObjectAt(0) as ASN1Integer).value
                val value2 = (dLSequence.getObjectAt(1) as ASN1Integer).value
                val bigInt1 = BigIntegers.asUnsignedByteArray(32, value1)
                val bigInt2 = BigIntegers.asUnsignedByteArray(32, value2)

                return bigInt1 + bigInt2
            } catch(e: Exception) {
                EversenseLogger.error(TAG, "Got exception during ecdsaSign - exception: $e")
                return null
            }
        }

        private fun generateEncryptionSalt(salt: ByteArray, i: Long): ByteArray {
            val wrapLong = ByteBuffer.wrap(salt).run {
                order(ByteOrder.LITTLE_ENDIAN)
                getLong()
            }

            val wrapLongLong = ((wrapLong and (-16384)) or i)
            val wrapByteArray = longToBytes(wrapLongLong)
            return wrapByteArray.reversed().toByteArray()
        }

        private fun getState(preference: SharedPreferences): EversenseSecureState {
            val stateJson = preference.getString(StorageKeys.SECURE_STATE, null) ?: "{}"
            return JSON.decodeFromString<EversenseSecureState>(stateJson)
        }

        private fun saveState(state: EversenseSecureState, preference: SharedPreferences) {
            preference.edit(commit = true) {
                putString(StorageKeys.SECURE_STATE, JSON.encodeToString(state))
            }
        }

        private fun aeadCCM(salt: ByteArray, data: ByteArray, prefix: ByteArray, forEncryption: Boolean, sessionKey: ByteArray): ByteArray? {
            try {
                return CCMBlockCipher.newInstance(AESEngine.newInstance()).run {
                    init(forEncryption,
                         AEADParameters(KeyParameter(sessionKey), salt.count() * 8, salt, prefix)
                    )

                    val bArr5 = ByteArray(getOutputSize(data.count()))
                    doFinal(bArr5, processBytes(data, 0, data.count(), bArr5, 0))
                    bArr5
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "AEAD-CCM encryption/decryption error: $e");
                e.printStackTrace()
                return null;
            }
        }

        private fun longToBytes(j: Long): ByteArray {
            val allocate = ByteBuffer.allocate(8)
            allocate.putLong(j)
            return allocate.array()
        }
    }
}