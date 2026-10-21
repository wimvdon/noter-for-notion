package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SealedBoxTest {
    /**
     * Sealed by the sign-in helper itself (`seal` in worker/noter-sign-in.mjs, run with Node) for
     * [privateKey], so the phone and the helper are known to agree.
     */
    private val privateKey = Base64.getDecoder().decode(
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgbZBZT7gjvxIIytkI7nxl74NnFb294/ZbSLcbmTyica+hRANCAATB3NaBiaSp2RVWJWiGVD5UvfgsSPz9X7rj1g1SMPfvzkhRbbF8SS/fQqXmfMaJWTpalDcNawtNuCniWmWohsIn",
    )
    private val sealed =
        "BJlaVlh6syMl1dVpnsbppaOqPPLAprCTFmK9CMAXtRfRijp6vXIXCTslktjeYUytyRsIr_InuTpPLjcSyOld2o4.NdLxZBw1VDJ6vGuQ." +
            "2R4Hb-RtEj4PtBv80CKc6w_SsxXbcOdcouaYRhU1UQyuicCbsduAekkvA5epqvyFFEm2R8vQPtY4VT-w7AliQPCHqZuK8m8A-btzQ86JE4XF3YkeU-O3Ltr" +
            "bYdH4iR979qmjXE3dcMHu0aumiNDNWvNM3ZdktsdJDTyAJf4GpObASzw-6kOModOsG-xP6aRGSEjgGeurIMvKKgpAN8gi-GTGdjIiJrmVmHU3pGtu_Tl4y28UXM9xJA"

    @Test
    fun `opens what the helper sealed`() {
        assertEquals(
            """{"access_token":"ntn_access","refresh_token":"ntn_refresh","workspace_id":"ws-1","workspace_name":"Home","bot_id":"bot-1","owner":{"type":"user","user":{"name":"Wim"}}}""",
            SealedBox.open(privateKey, sealed),
        )
    }

    @Test
    fun `another phone's key can't open it`() {
        assertNull(SealedBox.open(SealedBox.newKeys().privateKey, sealed))
    }

    @Test
    fun `a changed message isn't opened`() {
        val changed = sealed.dropLast(2) + if (sealed.endsWith("JA")) "JB" else "JA"
        assertNull(SealedBox.open(privateKey, changed))
        assertNull(SealedBox.open(privateKey, "not.sealed"))
        assertNull(SealedBox.open(privateKey, ""))
    }

    @Test
    fun `new keys are raw P-256 public keys, different each time`() {
        val a = SealedBox.newKeys()
        val b = SealedBox.newKeys()
        assertEquals(65, a.publicKey.size)
        assertEquals(4.toByte(), a.publicKey[0])
        assertNotEquals(SealedBox.base64Url(a.publicKey), SealedBox.base64Url(b.publicKey))
    }

    @Test
    fun `a message sealed for new keys, as the helper does, opens with them`() {
        repeat(20) {
            val keys = SealedBox.newKeys()
            val text = "{\"access_token\":\"ntn_$it\"}"
            assertEquals(text, SealedBox.open(keys.privateKey, seal(keys.publicKey, text)))
        }
    }

    /** The helper's `seal`, written with the JDK, for keys made in the test. */
    private fun seal(recipient: ByteArray, text: String): String {
        val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(BigInteger(1, recipient.copyOfRange(1, 33)), BigInteger(1, recipient.copyOfRange(33, 65)))
        val theirs = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
        val mine = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val shared = KeyAgreement.getInstance("ECDH").apply {
            init(mine.private)
            doPhase(theirs, true)
        }.generateSecret()
        val prk = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32), "HmacSHA256")) }.doFinal(shared)
        val key = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
            .doFinal("noter-sign-in-v1".toByteArray() + byteArrayOf(1))
        val nonce = ByteArray(12) { it.toByte() }
        val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        }.doFinal(text.toByteArray())
        val w = (mine.public as ECPublicKey).w
        fun fixed(v: BigInteger) = v.toByteArray().dropWhile { it == 0.toByte() }.toByteArray().let { ByteArray(32 - it.size) + it }
        val publicKey = byteArrayOf(4) + fixed(w.affineX) + fixed(w.affineY)
        return listOf(publicKey, nonce, ciphertext).joinToString(".") { SealedBox.base64Url(it) }
    }
}
