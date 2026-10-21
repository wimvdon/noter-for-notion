package be.wimvandonck.noter.logic

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A one-time key pair for a sign-in: the public half goes to Notion in the sign-in's `state`, the
 * private half stays on the phone ([privateKey], PKCS#8) to open what the helper sends back.
 */
class SealKeys(val privateKey: ByteArray, val publicKey: ByteArray)

/**
 * Opens what the sign-in helper (`worker/noter-sign-in.mjs`, `seal`) sealed for this phone: ECDH
 * on P-256 with the helper's one-time key, HKDF-SHA-256 and AES-256-GCM. Only the phone that
 * started the sign-in has the private key, so another app that catches the link learns nothing.
 */
object SealedBox {
    /** A new key pair; the public key is raw (65 bytes, as WebCrypto exports it). */
    fun newKeys(): SealKeys {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return SealKeys(pair.private.encoded, raw(pair.public as ECPublicKey))
    }

    /** The text in [sealed] (`<key>.<nonce>.<ciphertext>`, base64url), or null when it can't be opened with [privateKey]. */
    fun open(privateKey: ByteArray, sealed: String): String? = runCatching {
        val parts = sealed.split(".")
        require(parts.size == 3)
        val (theirKey, nonce, ciphertext) = parts.map { Base64.getUrlDecoder().decode(it) }
        val mine = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKey)) as ECPrivateKey
        val agreement = KeyAgreement.getInstance("ECDH").apply {
            init(mine)
            doPhase(publicKey(theirKey, mine), true)
        }
        val key = hkdf(agreement.generateSecret(), INFO.toByteArray())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        }
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun raw(key: ECPublicKey): ByteArray = byteArrayOf(4) + fixed(key.w.affineX) + fixed(key.w.affineY)

    /** [value] as exactly 32 bytes (BigInteger adds a sign byte or drops leading zeros). */
    private fun fixed(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(SIZE - bytes.size) + bytes
    }

    private fun publicKey(raw: ByteArray, like: ECPrivateKey): ECPublicKey {
        require(raw.size == 1 + 2 * SIZE && raw[0] == 4.toByte())
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 1 + SIZE)), BigInteger(1, raw.copyOfRange(1 + SIZE, raw.size)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, like.params)) as ECPublicKey
    }

    /** HKDF-SHA-256 (RFC 5869) with 32 zero bytes as salt, for one 32-byte key. */
    private fun hkdf(secret: ByteArray, info: ByteArray): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(SIZE), "HmacSHA256")) }
        val prk = extract.doFinal(secret)
        val expand = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
        return expand.doFinal(info + byteArrayOf(1))
    }

    private const val SIZE = 32

    /** The same words as the helper's SEAL_INFO. */
    private const val INFO = "noter-sign-in-v1"
}
